package io.github.sammeeeeeeee.higiene.ui.favourites

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammeeeeeeee.higiene.HigieneApp
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.Brands
import io.github.sammeeeeeeee.higiene.data.Repository
import io.github.sammeeeeeeee.higiene.data.UserSettings
import io.github.sammeeeeeeee.higiene.ui.LocalBottomSpace
import io.github.sammeeeeeeee.higiene.ui.components.HeroHeader
import io.github.sammeeeeeeee.higiene.ui.components.Tile
import io.github.sammeeeeeeee.higiene.ui.components.InfoCard
import io.github.sammeeeeeeee.higiene.ui.nearby.EstablishmentRow
import io.github.sammeeeeeeee.higiene.ui.theme.LocalDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class FavouritesViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as HigieneApp).graph
    val settings: StateFlow<UserSettings> = graph.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    data class Item(val fav: Repository.Favourite, val brand: Brands.Brand?)

    val items: StateFlow<List<Item>?> = combine(graph.repo.version, graph.settings.flow) { _, s -> s }
        .map { s ->
            graph.repo.favourites(s.lastCentre).map { Item(it, if (s.brandLogos && it.listing != null) graph.brands.match(it.name) else null) }
                .also { items ->
                    graph.recent.put(items.mapNotNull { i -> i.fav.listing?.let { io.github.sammeeeeeeee.higiene.data.RecentListings.Item(it.est, it.distanceM, i.brand, favourite = true) } })
                }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun FavouritesScreen(onOpen: (Long) -> Unit, vm: FavouritesViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        HeroHeader(stringResource(R.string.favourites_title), subtitle = stringResource(R.string.favourites_subtitle))
        val list = items
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = LocalBottomSpace.current + 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (list != null && list.isEmpty()) {
                item(key = "empty") {
                    InfoCard {
                        Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Text(stringResource(R.string.favourites_empty_title), style = LocalDisplay.current.section)
                        Text(stringResource(R.string.favourites_empty), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            items(list.orEmpty(), key = { it.fav.id }) { item ->
                val l = item.fav.listing
                if (l != null) {
                    EstablishmentRow(l.est, l.distanceM, item.brand, settings.miles, onClick = { onOpen(item.fav.id) }, modifier = Modifier.animateItem())
                } else {
                    Tile(onClick = { onOpen(item.fav.id) }, modifier = Modifier.animateItem()) {
                        Column(Modifier.padding(18.dp)) {
                            Text(item.fav.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(stringResource(R.string.favourite_removed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
