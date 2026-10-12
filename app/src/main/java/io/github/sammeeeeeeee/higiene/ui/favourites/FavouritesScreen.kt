package io.github.sammeeeeeeee.higiene.ui.favourites

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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

class FavouritesViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as HigieneApp).graph
    val settings: StateFlow<UserSettings> = graph.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    data class Item(val fav: Repository.Favourite, val brand: Brands.Brand?)

    val items: StateFlow<List<Item>?> = combine(graph.repo.version, graph.settings.flow) { _, s -> s }
        .map { s -> graph.repo.favourites(s.lastCentre).map { Item(it, if (s.brandLogos && it.listing != null) graph.brands.match(it.name) else null) } }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun FavouritesScreen(onOpen: (Long) -> Unit, vm: FavouritesViewModel = viewModel()) {
    val items by vm.items.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hero = LocalHero.current
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(hero.background)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(16.dp),
        ) {
            Text(stringResource(R.string.favourites_title), style = LocalDisplay.current.title, color = hero.headline)
            Text(stringResource(R.string.favourites_subtitle), style = MaterialTheme.typography.bodyMedium, color = hero.body)
        }
        val list = items
        if (list != null && list.isEmpty()) {
            InfoCard(Modifier.padding(16.dp)) { Text(stringResource(R.string.favourites_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(list.orEmpty(), key = { it.fav.id }) { item ->
                val l = item.fav.listing
                if (l != null) {
                    EstablishmentRow(l.est, l.distanceM, item.brand, settings.miles, onClick = { onOpen(item.fav.id) })
                } else {
                    Column(Modifier.fillMaxWidth().clickable { onOpen(item.fav.id) }.padding(16.dp)) {
                        Text(item.fav.name, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.favourite_removed), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Hairline(Modifier.padding(start = 72.dp))
            }
        }
    }
}
