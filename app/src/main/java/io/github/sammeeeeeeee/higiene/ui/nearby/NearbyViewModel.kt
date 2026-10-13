package io.github.sammeeeeeeee.higiene.ui.nearby

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.sammeeeeeeee.higiene.HigieneApp
import io.github.sammeeeeeeee.higiene.data.Brands
import io.github.sammeeeeeeee.higiene.data.BusinessType
import io.github.sammeeeeeeee.higiene.data.Centre
import io.github.sammeeeeeeee.higiene.data.Filters
import io.github.sammeeeeeeee.higiene.data.Listing
import io.github.sammeeeeeeee.higiene.data.Postcodes
import io.github.sammeeeeeeee.higiene.data.RecentListings
import io.github.sammeeeeeeee.higiene.data.Repository
import io.github.sammeeeeeeee.higiene.data.UserSettings
import io.github.sammeeeeeeee.higiene.location.LocationHelper
import io.github.sammeeeeeeee.higiene.ui.RefreshUi
import io.github.sammeeeeeeee.higiene.ui.refreshUiFlow
import io.github.sammeeeeeeee.higiene.work.Scheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Row(val listing: Listing, val brand: Brands.Brand?)

data class NearbyState(
    val hasData: Boolean? = null,
    val centre: Centre? = null,
    val locating: Boolean = false,
    val locationProblem: LocationProblem? = null,
    val filters: Filters = Filters(),
    val query: String = "",
    /** Set when the query reads as a postcode; offered as a new centre. */
    val place: Centre? = null,
    val fullPostcode: String? = null,
    val rows: List<Row> = emptyList(),
    val loading: Boolean = false,
    /** True once results for the current inputs have arrived, so "nothing found" isn't shown early. */
    val loaded: Boolean = false,
    val unlocated: Repository.Unlocated? = null,
    val types: List<BusinessType> = emptyList(),
    /** When the ratings data was last confirmed current (the FSA asks apps to show this). */
    val dataCheckedAt: Long? = null,
)

enum class LocationProblem { NO_PERMISSION, DISABLED, NO_FIX }

@OptIn(FlowPreview::class)
class NearbyViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = (app as HigieneApp).graph
    private val _state = MutableStateFlow(NearbyState())
    val state: StateFlow<NearbyState> = _state.asStateFlow()

    val settings: StateFlow<UserSettings> = graph.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, UserSettings())
    val refresh: StateFlow<RefreshUi> = refreshUiFlow(app) { graph.db.meta(io.github.sammeeeeeeee.higiene.work.RefreshWorker.META_LAST_ERROR) }
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.Eagerly, RefreshUi())

    private var locateJob: Job? = null

    init {
        viewModelScope.launch {
            val s = graph.settings.current()
            val has = withContext(Dispatchers.IO) { graph.repo.hasData() }
            _state.update { it.copy(hasData = has, centre = s.lastCentre) }
            if (s.lastCentre == null || s.lastCentre.fromGps) useMyLocation(quiet = true)
        }
        // Reload whenever inputs or the data change.
        viewModelScope.launch {
            combine(
                _state, graph.repo.version, settings,
            ) { st, v, s -> Triple(Key(st.centre, st.filters, st.query.trim(), st.hasData), v, s.brandLogos) }
                .distinctUntilChanged()
                .debounce(200)
                .collect { (key, _, logos) -> load(key, logos) }
        }
        // Pick up the first import finishing.
        viewModelScope.launch {
            graph.repo.version.collect {
                val has = withContext(Dispatchers.IO) { graph.repo.hasData() }
                _state.update { st -> st.copy(hasData = has) }
            }
        }
    }

    private data class Key(val centre: Centre?, val filters: Filters, val query: String, val hasData: Boolean?)

    private suspend fun load(key: Key, logos: Boolean) {
        if (key.hasData != true) return
        _state.update { it.copy(loading = true, loaded = false) }
        val result = withContext(Dispatchers.IO) {
            val types = graph.repo.types()
            val q = key.query
            val place = if (q.isNotEmpty()) graph.repo.resolvePlace(q) else null
            val full = (Postcodes.parse(q) as? Postcodes.Query.Full)?.key
            when {
                q.length >= 2 && place == null -> {
                    val list = graph.repo.search(q, key.centre, key.filters)
                    Loaded(list.map { Row(it, if (logos) graph.brands.match(it.est.name) else null) }, null, types, null, full)
                }
                key.centre != null && q.isEmpty() -> {
                    val n = graph.repo.nearby(key.centre, key.filters)
                    Loaded(n.listings.map { Row(it, if (logos) graph.brands.match(it.est.name) else null) }, n.unlocated, types, null, null)
                }
                else -> Loaded(emptyList(), null, types, place, full)
            }
        }
        graph.recent.put(result.rows.map { RecentListings.Item(it.listing.est, it.listing.distanceM, it.brand) })
        val checked = withContext(Dispatchers.IO) { graph.repo.dataInfo().checkedAt }
        _state.update {
            it.copy(
                rows = result.rows, unlocated = result.unlocated, types = result.types, place = result.place,
                fullPostcode = result.fullPostcode, loading = false, loaded = true, dataCheckedAt = checked,
            )
        }
    }

    private data class Loaded(
        val rows: List<Row>,
        val unlocated: Repository.Unlocated?,
        val types: List<BusinessType>,
        val place: Centre?,
        val fullPostcode: String?,
    )

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun setFilters(f: Filters) = _state.update { it.copy(filters = f) }

    fun setCentre(c: Centre) {
        locateJob?.cancel()
        _state.update { it.copy(centre = c, query = "", place = null, locating = false, locationProblem = null) }
        viewModelScope.launch { graph.settings.setLastCentre(c) }
    }

    fun useMyLocation(quiet: Boolean = false) {
        val ctx = getApplication<Application>()
        if (!LocationHelper.hasPermission(ctx)) {
            if (!quiet) _state.update { it.copy(locationProblem = LocationProblem.NO_PERMISSION) }
            return
        }
        if (!LocationHelper.isLocationEnabled(ctx)) {
            _state.update { it.copy(locationProblem = LocationProblem.DISABLED) }
            return
        }
        locateJob?.cancel()
        locateJob = viewModelScope.launch {
            _state.update { it.copy(locating = true, locationProblem = null) }
            val loc = LocationHelper.current(ctx)
            if (loc == null) {
                _state.update { it.copy(locating = false, locationProblem = LocationProblem.NO_FIX) }
                return@launch
            }
            val c = Centre(loc.latitude, loc.longitude, "", fromGps = true, approximate = loc.accuracy > 500)
            // An automatic fix at start-up must not wipe what the user has started typing.
            _state.update { if (quiet) it.copy(centre = c, locating = false) else it.copy(centre = c, locating = false, query = "", place = null) }
            graph.settings.setLastCentre(c)
        }
    }

    fun dismissLocationProblem() = _state.update { it.copy(locationProblem = null) }

    /**
     * Pull to refresh: check the FSA for newer ratings (a quick check when nothing changed), and if
     * the list follows your position, find it again. A place you chose stays chosen.
     */
    fun pullRefresh() {
        val c = _state.value.centre
        if (c == null || c.fromGps) useMyLocation(quiet = true)
        viewModelScope.launch {
            // Leave a check that is running alone; restart one that is waiting out a retry or for Wi-Fi.
            val policy = if (refresh.value.running) androidx.work.ExistingWorkPolicy.KEEP else androidx.work.ExistingWorkPolicy.REPLACE
            Scheduler.refreshNow(getApplication(), graph.settings.current().wifiOnly, policy = policy)
        }
    }

    fun useMobileData() = viewModelScope.launch { Scheduler.refreshNow(getApplication(), graph.settings.current().wifiOnly, anyNetwork = true) }

    fun permissionDenied() = _state.update { it.copy(locating = false, locationProblem = LocationProblem.NO_PERMISSION) }

    fun startDownload(anyNetwork: Boolean) {
        viewModelScope.launch {
            val s = graph.settings.current()
            Scheduler.refreshNow(getApplication(), s.wifiOnly, anyNetwork = anyNetwork)
            Scheduler.schedule(getApplication(), s.refresh, s.wifiOnly)
        }
    }

    /** Re-check after returning to the app (e.g. permission granted in system settings). */
    fun onResume() {
        viewModelScope.launch {
            delay(50)
            val has = withContext(Dispatchers.IO) { graph.repo.hasData() }
            _state.update { it.copy(hasData = has) }
        }
    }
}
