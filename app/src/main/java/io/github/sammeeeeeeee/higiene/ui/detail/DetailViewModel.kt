package io.github.sammeeeeeeee.higiene.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.sammeeeeeeee.higiene.HigieneApp
import io.github.sammeeeeeeee.higiene.data.Brands
import io.github.sammeeeeeeee.higiene.data.Establishment
import io.github.sammeeeeeeee.higiene.data.HistoryEntry
import io.github.sammeeeeeeee.higiene.data.OsmResult
import io.github.sammeeeeeeee.higiene.data.Repository
import io.github.sammeeeeeeee.higiene.data.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DetailState(
    val loading: Boolean = true,
    val est: Establishment? = null,
    val distanceM: Double? = null,
    val favourite: Boolean = false,
    val favouriteName: String? = null,
    val brand: Brands.Brand? = null,
    /** From OSM brand:wikidata; preferred over name matching. */
    val osmBrand: Brands.Brand? = null,
    val sameSite: List<Establishment> = emptyList(),
    val samePostcode: List<Establishment> = emptyList(),
    val history: List<HistoryEntry> = emptyList(),
    val osm: OsmResult? = null,
    val osmLoading: Boolean = false,
    val dataInfo: Repository.DataInfo? = null,
)

class DetailViewModel(app: Application, saved: SavedStateHandle) : AndroidViewModel(app) {
    private val graph = (app as HigieneApp).graph
    private val id: Long = checkNotNull(saved.get<Long>("id"))
    private val _state = MutableStateFlow(DetailState())
    val state: StateFlow<DetailState> = _state.asStateFlow()
    val settings: StateFlow<UserSettings> = graph.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())

    init {
        viewModelScope.launch {
            graph.repo.version.collect { load() }
        }
    }

    private suspend fun load() {
        val s = graph.settings.current()
        val loaded = withContext(Dispatchers.IO) {
            val e = graph.repo.establishment(id)
            val centre = s.lastCentre
            DetailState(
                loading = false,
                est = e,
                distanceM = if (e?.lat != null && e.lon != null && centre != null) Repository.distance(centre.lat, centre.lon, e.lat, e.lon) else null,
                favourite = graph.repo.isFavourite(id),
                favouriteName = graph.repo.favouriteName(id),
                brand = if (e != null && s.brandLogos) graph.brands.match(e.name) else null,
                sameSite = e?.let { graph.repo.sameSite(it) }.orEmpty(),
                samePostcode = e?.let { graph.repo.samePostcode(it) }.orEmpty(),
                history = graph.repo.history(id),
                dataInfo = graph.repo.dataInfo(),
            )
        }
        _state.update { loaded.copy(osm = it.osm, osmLoading = it.osmLoading, osmBrand = it.osmBrand) }
        if (s.osmDetails && _state.value.osm == null && loaded.est != null) loadOsm(s.brandLogos)
    }

    private fun loadOsm(logos: Boolean) {
        if (_state.value.osmLoading) return
        _state.update { it.copy(osmLoading = true) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { graph.osm.details(id) }
            // OSM's brand:wikidata is more reliable than matching names.
            val brand = if (logos && result is OsmResult.Found) {
                result.details.brandQid?.let { qid -> withContext(Dispatchers.IO) { graph.brands.byQid(qid) } }
            } else null
            _state.update { it.copy(osm = result, osmLoading = false, osmBrand = brand) }
        }
    }

    fun toggleFavourite(): Boolean {
        val on = !_state.value.favourite
        val name = _state.value.est?.name ?: _state.value.favouriteName ?: return false
        _state.update { it.copy(favourite = on) }
        viewModelScope.launch(Dispatchers.IO) { graph.repo.setFavourite(id, name, on) }
        return on
    }
}
