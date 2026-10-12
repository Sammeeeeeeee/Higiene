package io.github.sammeeeeeeee.higiene.ui.address

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammeeeeeeee.higiene.HigieneApp
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.Brands
import io.github.sammeeeeeeee.higiene.data.Establishment
import io.github.sammeeeeeeee.higiene.data.Filters
import io.github.sammeeeeeeee.higiene.data.Postcodes
import io.github.sammeeeeeeee.higiene.data.UserSettings
import io.github.sammeeeeeeee.higiene.ui.components.Hairline
import io.github.sammeeeeeeee.higiene.ui.components.InfoCard
import io.github.sammeeeeeeee.higiene.ui.nearby.EstablishmentRow
import io.github.sammeeeeeeee.higiene.ui.theme.LocalDisplay
import io.github.sammeeeeeeee.higiene.ui.theme.LocalHero
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A group of businesses sharing one first address line. */
data class Site(val address: String, val items: List<Pair<Establishment, Brands.Brand?>>)

/**
 * "Who's really cooking?" Lists every business registered at a postcode, grouped by address, or
 * every business with no published position in a council ([la] set).
 */
class ListViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val graph = (app as HigieneApp).graph
    val pc: String? = saved.get<String>("pc")
    val la: Int? = saved.get<Int>("la")
    val settings: StateFlow<UserSettings> = graph.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val councilName: String? get() = la?.let { graph.repo.authority(it)?.name }

    val sites: StateFlow<List<Site>?> = combine(graph.repo.version, graph.settings.flow) { _, s -> s }
        .map { s ->
            val list = when {
                pc != null -> graph.repo.atPostcode(pc)
                la != null -> graph.repo.unlocated(la, Filters())
                else -> emptyList()
            }
            fun brand(e: Establishment) = if (s.brandLogos) graph.brands.match(e.name) else null
            if (pc != null) {
                list.groupBy { it.siteKey ?: it.address ?: "" }
                    .map { (_, g) -> Site(g.first().address ?: "", g.map { it to brand(it) }) }
                    .sortedByDescending { it.items.size }
            } else {
                listOf(Site("", list.map { it to brand(it) }))
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun ListScreen(onBack: () -> Unit, onOpen: (Long) -> Unit, vm: ListViewModel = viewModel()) {
    val sites by vm.sites.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hero = LocalHero.current
    val total = sites?.sumOf { it.items.size } ?: 0
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(hero.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 4.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back), tint = hero.body) }
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(
                    if (vm.pc != null) stringResource(R.string.whos_cooking_at, Postcodes.format(vm.pc))
                    else stringResource(R.string.unlocated_screen_title, vm.councilName ?: ""),
                    style = LocalDisplay.current.title,
                    color = hero.headline,
                )
                if (sites != null) {
                    Text(
                        if (vm.pc != null) pluralStringResource(R.plurals.address_summary, total, total, sites!!.size)
                        else pluralStringResource(R.plurals.unlocated_summary, total, total),
                        style = MaterialTheme.typography.bodyMedium,
                        color = hero.body,
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize().navigationBarsPadding()) {
            if (vm.pc != null) {
                item {
                    InfoCard(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.whos_cooking_explainer), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                item {
                    InfoCard(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.unlocated_body), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            sites.orEmpty().forEach { site ->
                if (vm.pc != null) {
                    item(key = "h" + site.address + site.items.first().first.id) {
                        Text(
                            site.address.ifEmpty { stringResource(R.string.address_unknown) } +
                                if (site.items.size > 1) " · " + pluralStringResource(R.plurals.businesses, site.items.size, site.items.size) else "",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                }
                items(site.items, key = { it.first.id }) { (e, brand) ->
                    EstablishmentRow(e, null, brand, settings.miles, onClick = { onOpen(e.id) })
                    Hairline(Modifier.padding(start = 72.dp))
                }
            }
        }
    }
}

