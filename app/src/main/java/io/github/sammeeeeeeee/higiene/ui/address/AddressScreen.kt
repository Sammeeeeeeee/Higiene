package io.github.sammeeeeeeee.higiene.ui.address

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import io.github.sammeeeeeeee.higiene.ui.LocalBottomSpace
import io.github.sammeeeeeeee.higiene.ui.components.HeroHeader
import io.github.sammeeeeeeee.higiene.ui.components.InfoCard
import io.github.sammeeeeeeee.higiene.ui.components.Tag
import io.github.sammeeeeeeee.higiene.ui.nearby.EstablishmentRow
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
    val councilName: StateFlow<String?> = kotlinx.coroutines.flow.flow { emit(la?.let { graph.repo.authority(it)?.name }) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
    val council by vm.councilName.collectAsStateWithLifecycle()
    val total = sites?.sumOf { it.items.size } ?: 0
    Column(Modifier.fillMaxSize()) {
        HeroHeader(
            title = if (vm.pc != null) stringResource(R.string.whos_cooking_at, Postcodes.format(vm.pc)) else stringResource(R.string.unlocated_screen_title, council ?: ""),
            onBack = onBack,
            subtitle = sites?.let {
                if (vm.pc != null) {
                    stringResource(
                        R.string.address_summary,
                        pluralStringResource(R.plurals.businesses, total, total),
                        pluralStringResource(R.plurals.addresses, it.size, it.size),
                    )
                }
                else pluralStringResource(R.plurals.unlocated_summary, total, total)
            },
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = LocalBottomSpace.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "intro") {
                InfoCard {
                    Text(stringResource(if (vm.pc != null) R.string.whos_cooking_explainer else R.string.unlocated_body), style = MaterialTheme.typography.bodyMedium)
                }
            }
            sites.orEmpty().forEach { site ->
                if (vm.pc != null) {
                    item(key = "h" + site.items.first().first.id) {
                        Row(Modifier.padding(start = 4.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                site.address.ifEmpty { stringResource(R.string.address_unknown) },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (site.items.size > 1) {
                                Spacer(Modifier.width(8.dp))
                                Tag(pluralStringResource(R.plurals.businesses, site.items.size, site.items.size), strong = true)
                            }
                        }
                    }
                }
                items(site.items, key = { it.first.id }) { (e, brand) ->
                    EstablishmentRow(e, null, brand, settings.miles, onClick = { onOpen(e.id) })
                }
            }
        }
    }
}
