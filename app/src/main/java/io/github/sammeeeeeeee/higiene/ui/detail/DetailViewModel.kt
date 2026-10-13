package io.github.sammeeeeeeee.higiene.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import io.github.sammeeeeeeee.higiene.HigieneApp
import io.github.sammeeeeeeee.higiene.data.Brands
import io.github.sammeeeeeeee.higiene.data.Establishment
import io.github.sammeeeeeeee.higiene.data.HistoryEntry
import io.github.sammeeeeeeee.higiene.data.GooglePlace
import io.github.sammeeeeeeee.higiene.data.OsmDetails
import io.github.sammeeeeeeee.higiene.data.StreetPhoto
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
    /** From the weekly OpenStreetMap file; null when OSM has nothing for this business. */
    val osm: OsmDetails? = null,
    /** Only with the user's Google key; kept for this screen only. */
    val google: GooglePlace? = null,
    /** Street View (Google key) or Mapillary (token); null without a key or a photo. */
    val photo: StreetPhoto? = null,
    /** A photo of the business from Google, with its author; only with the user's key. */
    val googlePhoto: GooglePhotoShown? = null,
    val dataInfo: Repository.DataInfo? = null,
)

data class GooglePhotoShown(val uri: String, val photo: GooglePlace.Photo)

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
                osm = if (s.osmDetails) graph.osm.details(id) else null,
            )
        }
        _state.update { loaded.copy(osmBrand = it.osmBrand, google = it.google, photo = it.photo, googlePhoto = it.googlePhoto) }
        val e = loaded.est ?: return
        // OSM's brand:wikidata is more reliable than matching names.
        if (s.brandLogos && loaded.osm?.brandQid != null && _state.value.osmBrand == null) {
            viewModelScope.launch {
                val brand = withContext(Dispatchers.IO) { runCatching { graph.brands.byQid(loaded.osm.brandQid) }.getOrNull() }
                _state.update { it.copy(osmBrand = brand) }
            }
        }
        if (!extrasStarted) {
            extrasStarted = true
            loadExtras(e, s)
        }
    }

    private var extrasStarted = false

    /**
     * Google details and a street photo, only with the user's own keys. Any failure (no match, cap
     * reached, key refused, offline) just leaves the card out.
     */
    private fun loadExtras(e: Establishment, s: UserSettings) {
        if (s.googleKey.isNotBlank()) {
            viewModelScope.launch {
                val place = withContext(Dispatchers.IO) { runCatching { graph.google.place(e, s.googleKey) }.getOrNull() }
                if (place == null || !place.hasDetails) return@launch
                // A caterer or van serves people elsewhere: a street photo of its base would mislead.
                if (place.serviceAreaOnly) streetPhotoDropped = true
                _state.update { it.copy(google = place, photo = if (place.serviceAreaOnly) null else it.photo) }
                val first = place.photos.firstOrNull { it.width >= 400 } ?: return@launch
                val uri = withContext(Dispatchers.IO) { runCatching { graph.google.photoUri(first, s.googleKey) }.getOrNull() }
                if (uri != null) _state.update { it.copy(googlePhoto = GooglePhotoShown(uri, first)) }
            }
        }
        if (s.googleKey.isNotBlank() || s.mapillaryToken.isNotBlank()) {
            viewModelScope.launch {
                val photo = withContext(Dispatchers.IO) {
                    val google = if (s.googleKey.isNotBlank()) runCatching { graph.google.streetPhoto(e, s.googleKey) }.getOrNull() else null
                    google ?: if (s.mapillaryToken.isNotBlank()) runCatching { graph.mapillary.photo(e, s.mapillaryToken) }.getOrNull() else null
                }
                if (photo != null && !streetPhotoDropped) _state.update { it.copy(photo = photo) }
            }
        }
    }

    /** Set from the main thread when Google says the business serves customers elsewhere. */
    private var streetPhotoDropped = false

    /** The image itself failed to load (e.g. the key is not enabled for Street View): drop the card. */
    fun photoFailed() = _state.update { it.copy(photo = null) }

    fun googlePhotoFailed() = _state.update { it.copy(googlePhoto = null) }

    fun toggleFavourite(): Boolean {
        val on = !_state.value.favourite
        val name = _state.value.est?.name ?: _state.value.favouriteName ?: return false
        _state.update { it.copy(favourite = on) }
        viewModelScope.launch(Dispatchers.IO) { graph.repo.setFavourite(id, name, on) }
        return on
    }
}
