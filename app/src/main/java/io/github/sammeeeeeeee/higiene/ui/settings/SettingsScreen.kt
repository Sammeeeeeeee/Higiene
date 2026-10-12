package io.github.sammeeeeeeee.higiene.ui.settings

import android.app.Application
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
    fun setOsm(v: Boolean) = viewModelScope.launch { graph.settings.setOsmDetails(v) }
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
