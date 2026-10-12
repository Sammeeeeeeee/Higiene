package io.github.sammeeeeeeee.higiene.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.LinearProgressIndicator
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
import io.github.sammeeeeeeee.higiene.ui.components.Hairline
import io.github.sammeeeeeeee.higiene.ui.components.InfoCard
import io.github.sammeeeeeeee.higiene.ui.components.PillChip
import io.github.sammeeeeeeee.higiene.ui.components.PrimaryPill
import io.github.sammeeeeeeee.higiene.ui.components.SectionTitle
import io.github.sammeeeeeeee.higiene.ui.components.TextLink
import io.github.sammeeeeeeee.higiene.ui.nearby.SwitchRow
import io.github.sammeeeeeeee.higiene.ui.refreshUiFlow
import io.github.sammeeeeeeee.higiene.ui.theme.Fsa
import io.github.sammeeeeeeee.higiene.ui.theme.LocalDisplay
import io.github.sammeeeeeeee.higiene.ui.theme.LocalHero
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
    val refresh: StateFlow<RefreshUi> = refreshUiFlow(app).stateIn(viewModelScope, SharingStarted.Eagerly, RefreshUi())

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
    fun setOsm(v: Boolean) = viewModelScope.launch { graph.settings.setOsmDetails(v) }
    fun setLogos(v: Boolean) = viewModelScope.launch { graph.settings.setBrandLogos(v) }
    fun setMiles(v: Boolean) = viewModelScope.launch { graph.settings.setMiles(v) }
    fun refreshNow(anyNetwork: Boolean) = viewModelScope.launch {
        Scheduler.refreshNow(getApplication(), graph.settings.current().wifiOnly, anyNetwork = anyNetwork)
    }
}

private val stamp = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)

private fun Long.stamp() = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(stamp)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onLicences: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val hero = LocalHero.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(hero.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.settings_title), style = LocalDisplay.current.title, color = hero.headline)
        }
        Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Data
            SectionTitle(stringResource(R.string.settings_data))
            InfoCard {
                val d = info?.data
                if (d != null && d.rows > 0) {
                    Text(stringResource(R.string.settings_rows, NumberFormat.getIntegerInstance(Locale.UK).format(d.rows)), style = MaterialTheme.typography.titleMedium)
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
                info?.lastError?.let { Text(stringResource(R.string.settings_last_error, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                when {
                    refresh.running -> {
                        Text(stringResource(R.string.progress_title), style = MaterialTheme.typography.bodyMedium)
                        val f = refresh.fraction
                        if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth(), color = Fsa.Green)
                        else LinearProgressIndicator(Modifier.fillMaxWidth(), color = Fsa.Green)
                    }
                    refresh.waiting -> {
                        Text(stringResource(if (refresh.waitingForWifi) R.string.waiting_for_wifi else R.string.waiting_for_network), style = MaterialTheme.typography.bodyMedium)
                        if (refresh.waitingForWifi) TextLink(stringResource(R.string.use_mobile_data), onClick = { vm.refreshNow(true) })
                    }
                    else -> PrimaryPill(stringResource(R.string.refresh_now), onClick = { vm.refreshNow(false) }, icon = Icons.Rounded.Refresh)
                }
                Text(stringResource(R.string.settings_size_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SectionTitle(stringResource(R.string.settings_refresh))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(RefreshInterval.DAILY to R.string.refresh_daily, RefreshInterval.WEEKLY to R.string.refresh_weekly, RefreshInterval.MANUAL to R.string.refresh_manual)
                    .forEach { (v, label) -> PillChip(stringResource(label), s.refresh == v, { vm.setRefresh(v) }) }
            }
            SwitchRow(stringResource(R.string.settings_wifi_only), s.wifiOnly, stringResource(R.string.settings_wifi_only_sub)) { vm.setWifiOnly(it) }
            Hairline()

            SectionTitle(stringResource(R.string.settings_extras))
            SwitchRow(stringResource(R.string.settings_osm), s.osmDetails, stringResource(R.string.settings_osm_sub)) { vm.setOsm(it) }
            SwitchRow(stringResource(R.string.settings_logos), s.brandLogos, stringResource(R.string.settings_logos_sub)) { vm.setLogos(it) }
            SwitchRow(stringResource(R.string.settings_miles), s.miles) { vm.setMiles(it) }
            Hairline()

            SectionTitle(stringResource(R.string.settings_privacy))
            Text(stringResource(R.string.privacy_body), style = MaterialTheme.typography.bodyMedium)
            Hairline()

            SectionTitle(stringResource(R.string.settings_about))
            Text(stringResource(R.string.about_body, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.not_endorsed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextLink(stringResource(R.string.licences_title), onClick = onLicences)
            TextLink(stringResource(R.string.source_code), onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Sammeeeeeeee/Higiene"))) }
            })
            Spacer(Modifier.height(24.dp))
        }
    }
}
