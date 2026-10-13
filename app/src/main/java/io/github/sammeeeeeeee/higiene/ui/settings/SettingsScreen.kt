package io.github.sammeeeeeeee.higiene.ui.settings

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import io.github.sammeeeeeeee.higiene.data.Api
import io.github.sammeeeeeeee.higiene.data.ApiBudget
import io.github.sammeeeeeeee.higiene.data.GoogleClient
import io.github.sammeeeeeeee.higiene.ui.components.Expandable
import io.github.sammeeeeeeee.higiene.ui.components.Hairline
import io.github.sammeeeeeeee.higiene.ui.components.tactileClick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.graphics.Color
import io.github.sammeeeeeeee.higiene.ui.LocalBottomSpace
import io.github.sammeeeeeeee.higiene.ui.components.DarkCard
import io.github.sammeeeeeeee.higiene.ui.components.HeroHeader
import io.github.sammeeeeeeee.higiene.ui.components.OutlinePill
import io.github.sammeeeeeeee.higiene.ui.components.PillItem
import io.github.sammeeeeeeee.higiene.ui.components.SegmentedPills
import io.github.sammeeeeeeee.higiene.ui.nearby.PillProgress
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammeeeeeeee.higiene.BuildConfig
import io.github.sammeeeeeeee.higiene.HigieneApp
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.RefreshInterval
import io.github.sammeeeeeeee.higiene.data.Repository
import io.github.sammeeeeeeee.higiene.data.UserSettings
import io.github.sammeeeeeeee.higiene.ui.RefreshUi
import io.github.sammeeeeeeee.higiene.ui.components.InfoCard
import io.github.sammeeeeeeee.higiene.ui.components.PrimaryPill
import io.github.sammeeeeeeee.higiene.ui.components.TextLink
import io.github.sammeeeeeeee.higiene.ui.nearby.SwitchRow
import io.github.sammeeeeeeee.higiene.ui.refreshUiFlow
import io.github.sammeeeeeeee.higiene.ui.theme.Fsa
import io.github.sammeeeeeeee.higiene.ui.theme.LocalDisplay
import io.github.sammeeeeeeee.higiene.work.RefreshWorker
import io.github.sammeeeeeeee.higiene.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as HigieneApp).graph
    val settings: StateFlow<UserSettings> = graph.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val refresh: StateFlow<RefreshUi> = refreshUiFlow(app) { graph.db.meta(RefreshWorker.META_LAST_ERROR) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, RefreshUi())

    data class Info(val data: Repository.DataInfo, val lastError: String?)

    val info: StateFlow<Info?> = combine(graph.repo.version, refresh) { _, _ -> Unit }
        .map { Info(graph.repo.dataInfo(), graph.db.meta(RefreshWorker.META_LAST_ERROR)) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private fun reschedule() = viewModelScope.launch {
        val s = graph.settings.current()
        Scheduler.schedule(getApplication(), s.refresh, s.wifiOnly)
    }

    fun setRefresh(v: RefreshInterval) = viewModelScope.launch { graph.settings.setRefresh(v); reschedule() }
    fun setWifiOnly(v: Boolean) = viewModelScope.launch { graph.settings.setWifiOnly(v); reschedule() }
    fun setOsm(v: Boolean) = viewModelScope.launch {
        graph.settings.setOsmDetails(v)
        if (v) {
            // Fetch the file now rather than at the next scheduled refresh. KEEP: a refresh that is
            // already queued or running reads this setting before its OSM step, so don't restart it.
            if (withContext(Dispatchers.IO) { graph.osm.rows() } == 0) {
                Scheduler.refreshNow(getApplication(), graph.settings.current().wifiOnly, policy = androidx.work.ExistingWorkPolicy.KEEP)
            }
        } else {
            withContext(Dispatchers.IO) { graph.osm.clear() }
            graph.repo.notifyChanged()
        }
    }

    /** Result of the last key check, per service. */
    sealed interface Check {
        data object Idle : Check
        data object Checking : Check
        data object Ok : Check
        data class Problem(val api: String, val message: String) : Check
        data object Offline : Check
    }

    private val _googleCheck = MutableStateFlow<Check>(Check.Idle)
    val googleCheck: StateFlow<Check> = _googleCheck.asStateFlow()
    private val _mapillaryCheck = MutableStateFlow<Check>(Check.Idle)
    val mapillaryCheck: StateFlow<Check> = _mapillaryCheck.asStateFlow()

    val identity get() = graph.identity

    data class KeyUsage(val details: ApiBudget.Usage, val photos: ApiBudget.Usage, val street: ApiBudget.Usage, val mapillary: ApiBudget.Usage)

    private val usageTick = MutableStateFlow(0)
    val usage: StateFlow<KeyUsage?> = usageTick
        .map {
            KeyUsage(
                graph.budget.usage(Api.GOOGLE_DETAILS),
                graph.budget.usage(Api.GOOGLE_PHOTOS),
                graph.budget.usage(Api.GOOGLE_STREETVIEW),
                graph.budget.usage(Api.MAPILLARY),
            )
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun refreshUsage() = usageTick.update { it + 1 }

    private var googleJob: kotlinx.coroutines.Job? = null
    private var mapillaryJob: kotlinx.coroutines.Job? = null

    /** Saves the key and checks it; a newer save or a removal cancels an unfinished check. */
    fun saveGoogleKey(key: String) {
        googleJob?.cancel()
        googleJob = viewModelScope.launch { checkGoogle(key) }
    }

    fun saveMapillaryToken(token: String) {
        mapillaryJob?.cancel()
        mapillaryJob = viewModelScope.launch { checkMapillary(token) }
    }

    private suspend fun checkGoogle(key: String) {
        graph.settings.setGoogleKey(key)
        if (key.isBlank()) {
            _googleCheck.value = Check.Idle
            return
        }
        _googleCheck.value = Check.Checking
        _googleCheck.value = withContext(Dispatchers.IO) {
            runCatching { graph.google.checkKey(key.trim()) }.fold(
                onSuccess = { if (it is GoogleClient.KeyCheck.Problem) Check.Problem(it.api, it.message) else Check.Ok },
                onFailure = { Check.Offline },
            )
        }
    }

    private suspend fun checkMapillary(token: String) {
        graph.settings.setMapillaryToken(token)
        if (token.isBlank()) {
            _mapillaryCheck.value = Check.Idle
            return
        }
        _mapillaryCheck.value = Check.Checking
        _mapillaryCheck.value = withContext(Dispatchers.IO) {
            runCatching { graph.mapillary.checkToken(token.trim()) }.fold(
                onSuccess = { problem -> if (problem != null) Check.Problem("Mapillary", problem) else Check.Ok },
                onFailure = { Check.Offline },
            )
        }
    }
    fun setLogos(v: Boolean) = viewModelScope.launch { graph.settings.setBrandLogos(v) }
    fun setMiles(v: Boolean) = viewModelScope.launch { graph.settings.setMiles(v) }
    fun refreshNow(anyNetwork: Boolean) = viewModelScope.launch {
        Scheduler.refreshNow(getApplication(), graph.settings.current().wifiOnly, anyNetwork = anyNetwork)
    }
}

private val stamp = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)

private fun Long.stamp() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(stamp)

@Composable
fun SettingsScreen(onLicences: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HeroHeader(stringResource(R.string.settings_title))
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = LocalBottomSpace.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Data
            DarkCard(stringResource(R.string.settings_data).uppercase()) {
                val d = info?.data
                if (d != null && d.rows > 0) {
                    Text(NumberFormat.getIntegerInstance(Locale.UK).format(d.rows), style = LocalDisplay.current.hero, color = Fsa.Lime)
                    Text(stringResource(R.string.settings_rows_label), style = MaterialTheme.typography.titleMedium)
                    d.lastModified?.let { Text(stringResource(R.string.settings_file_date, it), style = MaterialTheme.typography.bodySmall) }
                    d.importedAt?.let { Text(stringResource(R.string.settings_imported, it.stamp()), style = MaterialTheme.typography.bodySmall) }
                    d.checkedAt?.let { Text(stringResource(R.string.settings_checked, it.stamp()), style = MaterialTheme.typography.bodySmall) }
                    Text(
                        if (d.hasPostcodes) stringResource(R.string.settings_postcodes_ok) else stringResource(R.string.settings_postcodes_missing),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(stringResource(R.string.settings_no_data), style = MaterialTheme.typography.bodyMedium)
                }
                info?.lastError?.let { Text(stringResource(R.string.settings_last_error, it), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFB4AB)) }
                when {
                    refresh.running -> {
                        Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.bodyMedium)
                        PillProgress(refresh.fraction, height = 10.dp)
                    }
                    refresh.retrying -> {
                        Text(stringResource(R.string.retrying), style = MaterialTheme.typography.bodyMedium)
                        if (refresh.waitingForWifi) TextLink(stringResource(R.string.use_mobile_data), onClick = { vm.refreshNow(true) }, color = Fsa.Lime)
                    }
                    refresh.waiting -> {
                        Text(stringResource(if (refresh.waitingForWifi) R.string.waiting_for_wifi else R.string.waiting_for_network), style = MaterialTheme.typography.bodyMedium)
                        if (refresh.waitingForWifi) TextLink(stringResource(R.string.use_mobile_data), onClick = { vm.refreshNow(true) }, color = Fsa.Lime)
                    }
                    else -> PrimaryPill(stringResource(R.string.refresh_now), onClick = { vm.refreshNow(false) }, icon = Icons.Rounded.Refresh)
                }
                Text(stringResource(R.string.settings_size_note), style = MaterialTheme.typography.bodySmall)
            }

            InfoCard {
                Text(stringResource(R.string.settings_refresh), style = LocalDisplay.current.section)
                val intervals = listOf(RefreshInterval.DAILY to R.string.refresh_daily, RefreshInterval.WEEKLY to R.string.refresh_weekly, RefreshInterval.MANUAL to R.string.refresh_manual)
                SegmentedPills(
                    items = intervals.map { PillItem(stringResource(it.second)) },
                    selected = intervals.indexOfFirst { it.first == s.refresh },
                    onSelect = { vm.setRefresh(intervals[it].first) },
                    track = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                )
                SwitchRow(stringResource(R.string.settings_wifi_only), s.wifiOnly, stringResource(R.string.settings_wifi_only_sub)) { vm.setWifiOnly(it) }
            }

            InfoCard {
                Text(stringResource(R.string.settings_extras), style = LocalDisplay.current.section)
                SwitchRow(stringResource(R.string.settings_osm), s.osmDetails, stringResource(R.string.settings_osm_sub)) { vm.setOsm(it) }
                SwitchRow(stringResource(R.string.settings_logos), s.brandLogos, stringResource(R.string.settings_logos_sub)) { vm.setLogos(it) }
                SwitchRow(stringResource(R.string.settings_miles), s.miles) { vm.setMiles(it) }
            }

            KeysCard(s, vm)

            InfoCard {
                Text(stringResource(R.string.settings_privacy), style = LocalDisplay.current.section)
                Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
            }

            InfoCard {
                Text(stringResource(R.string.settings_about), style = LocalDisplay.current.section)
                Text(stringResource(R.string.about_body, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                Text(stringResource(R.string.not_endorsed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinePill(stringResource(R.string.licences_short), onClick = onLicences)
                    OutlinePill(stringResource(R.string.source_code), onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sammeeeeeeee/Higiene"))) }
                    })
                }
            }
        }
    }
}

@Composable
private fun KeysCard(s: UserSettings, vm: SettingsViewModel) {
    val usage by vm.usage.collectAsStateWithLifecycle()
    val googleCheck by vm.googleCheck.collectAsStateWithLifecycle()
    val mapillaryCheck by vm.mapillaryCheck.collectAsStateWithLifecycle()
    LaunchedEffect(googleCheck, mapillaryCheck) { vm.refreshUsage() }
    InfoCard {
        Text(stringResource(R.string.keys_title), style = LocalDisplay.current.section)
        Text(stringResource(R.string.keys_body), style = MaterialTheme.typography.bodyMedium)

        // Google ------------------------------------------------------------------------------------
        KeyField(stringResource(R.string.key_google_label), s.googleKey, googleCheck, vm::saveGoogleKey)
        val u = usage
        if (s.googleKey.isNotBlank() && u != null) {
            UsageLine(R.string.usage_lookups, u.details, Api.GOOGLE_DETAILS)
            UsageLine(R.string.usage_photos, u.photos, Api.GOOGLE_PHOTOS)
            UsageLine(R.string.usage_street, u.street, Api.GOOGLE_STREETVIEW)
        }
        Expandable(stringResource(R.string.key_how_google)) {
            Text(stringResource(R.string.key_google_steps), style = MaterialTheme.typography.bodyMedium)
            CopyRow(stringResource(R.string.key_package), vm.identity.packageName)
            vm.identity.certSha1Colons?.let { CopyRow(stringResource(R.string.key_sha1), it) }
            Text(
                stringResource(
                    R.string.key_google_limits,
                    Api.GOOGLE_DETAILS.perDay, Api.GOOGLE_PHOTOS.perDay, Api.GOOGLE_STREETVIEW.perDay,
                    Api.GOOGLE_DETAILS.perMonth, Api.GOOGLE_PHOTOS.perMonth, Api.GOOGLE_STREETVIEW.perMonth,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinkRow("console.cloud.google.com", "https://console.cloud.google.com/")
        }

        Hairline()

        // Mapillary ---------------------------------------------------------------------------------
        KeyField(stringResource(R.string.key_mapillary_label), s.mapillaryToken, mapillaryCheck, vm::saveMapillaryToken)
        if (s.mapillaryToken.isNotBlank() && u != null) UsageLine(R.string.usage_mapillary, u.mapillary, Api.MAPILLARY)
        Expandable(stringResource(R.string.key_how_mapillary)) {
            Text(stringResource(R.string.key_mapillary_steps), style = MaterialTheme.typography.bodyMedium)
            LinkRow("mapillary.com/dashboard/developers", "https://www.mapillary.com/dashboard/developers")
        }
    }
}

@Composable
private fun UsageLine(label: Int, u: ApiBudget.Usage, api: Api) {
    Text(
        stringResource(R.string.key_usage_line, stringResource(label), u.today, api.perDay, u.month, api.perMonth),
        style = MaterialTheme.typography.bodySmall,
        color = if (u.today >= api.perDay || u.month >= api.perMonth) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** A key entry: hidden by default, saved and checked with free calls when the user taps Save. */
@Composable
private fun KeyField(label: String, saved: String, check: SettingsViewModel.Check, onSave: (String) -> Unit) {
    var text by rememberSaveable(saved) { mutableStateOf(saved) }
    var visible by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.trim() },
            label = { Text(label) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = stringResource(if (visible) R.string.key_hide else R.string.key_show),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            PrimaryPill(stringResource(R.string.key_save), onClick = { onSave(text) }, enabled = text.isNotBlank() && check != SettingsViewModel.Check.Checking)
            if (saved.isNotBlank()) TextLink(stringResource(R.string.key_remove), onClick = { text = ""; onSave("") })
        }
        when (check) {
            SettingsViewModel.Check.Idle -> Unit
            SettingsViewModel.Check.Checking -> Text(stringResource(R.string.key_checking), style = MaterialTheme.typography.bodySmall)
            SettingsViewModel.Check.Ok -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Fsa.Green, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.key_ok), style = MaterialTheme.typography.bodySmall)
            }
            is SettingsViewModel.Check.Problem -> Text(
                stringResource(R.string.key_problem, check.api, check.message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            SettingsViewModel.Check.Offline -> Text(stringResource(R.string.key_offline), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A value the user needs to paste into the Google console, with a copy button. */
@Composable
private fun CopyRow(label: String, value: String) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .tactileClick {
                val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                cm?.setPrimaryClip(android.content.ClipData.newPlainText(label, value))
                // Android 13+ shows its own confirmation.
                if (android.os.Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(context, R.string.copied, android.widget.Toast.LENGTH_SHORT).show()
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Rounded.ContentCopy, contentDescription = stringResource(R.string.copy), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun LinkRow(text: String, url: String) {
    val context = LocalContext.current
    TextLink(stringResource(R.string.open_link, text), onClick = {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    })
}
