package io.github.sammeeeeeeee.higiene.ui.nearby

import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.Filters
import io.github.sammeeeeeeee.higiene.data.SortOrder
import io.github.sammeeeeeeee.higiene.ui.RefreshUi
import io.github.sammeeeeeeee.higiene.ui.components.Hairline
import io.github.sammeeeeeeee.higiene.ui.components.InfoCard
import io.github.sammeeeeeeee.higiene.ui.components.OutlinePill
import io.github.sammeeeeeeee.higiene.ui.components.PillChip
import io.github.sammeeeeeeee.higiene.ui.components.PrimaryPill
import io.github.sammeeeeeeee.higiene.ui.components.TextLink
import io.github.sammeeeeeeee.higiene.ui.theme.Fsa
import io.github.sammeeeeeeee.higiene.ui.theme.LocalDisplay
import io.github.sammeeeeeeee.higiene.ui.theme.LocalHero
import io.github.sammeeeeeeee.higiene.work.RefreshWorker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NearbyScreen(
    onOpen: (Long) -> Unit,
    onOpenAddress: (String) -> Unit,
    onOpenUnlocated: (Int) -> Unit,
    vm: NearbyViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) vm.useMyLocation() else vm.dismissLocationProblem()
    }
    fun askLocation() = permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    when (state.hasData) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        false -> Onboarding(refresh, wifiOnly = settings.wifiOnly, onStart = { vm.startDownload(anyNetwork = it) })
        true -> Column(Modifier.fillMaxSize()) {
            Header(
                title = when {
                    state.centre == null -> stringResource(R.string.nearby_title_where)
                    state.centre!!.fromGps -> stringResource(R.string.nearby_title_near_you)
                    else -> stringResource(R.string.nearby_title_near, state.centre!!.label)
                },
                query = state.query,
                onQuery = vm::setQuery,
                locating = state.locating,
                onLocate = { if (LocalHelper.has(context)) vm.useMyLocation() else askLocation() },
            )
            if (refresh.running || (state.loading && state.rows.isEmpty())) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Fsa.Lime)
            FilterRow(state.filters, onOpenFilters = { showFilters = true }, onChange = vm::setFilters)
            Hairline()
            Box(Modifier.weight(1f)) {
                Results(
                    state = state,
                    miles = settings.miles,
                    onOpen = onOpen,
                    onOpenAddress = onOpenAddress,
                    onOpenUnlocated = onOpenUnlocated,
                    onPlace = vm::setCentre,
                    onLocate = { if (LocalHelper.has(context)) vm.useMyLocation() else askLocation() },
                    onLocationSettings = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    onDismissProblem = vm::dismissLocationProblem,
                )
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            FiltersSheet(state.filters, state.types.map { it.id to it.name }, onChange = vm::setFilters)
        }
    }
}

/** Avoids importing LocationHelper into every composable lambda. */
private object LocalHelper {
    fun has(context: android.content.Context) = io.github.sammeeeeeeee.higiene.location.LocationHelper.hasPermission(context)
}

@Composable
private fun Header(title: String, query: String, onQuery: (String) -> Unit, locating: Boolean, onLocate: () -> Unit) {
    val hero = LocalHero.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(hero.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = LocalDisplay.current.title,
                color = hero.headline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onLocate) {
                if (locating) CircularProgressIndicator(Modifier.size(22.dp), color = hero.accent, strokeWidth = 2.dp)
                else Icon(Icons.Rounded.MyLocation, contentDescription = stringResource(R.string.use_my_location), tint = hero.body)
            }
        }
        TextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.clear_search)) }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedTextColor = Fsa.Obsidian,
                unfocusedTextColor = Fsa.Obsidian,
                focusedPlaceholderColor = Fsa.Slate,
                unfocusedPlaceholderColor = Fsa.Slate,
                focusedLeadingIconColor = Fsa.Slate,
                unfocusedLeadingIconColor = Fsa.Slate,
                focusedTrailingIconColor = Fsa.Slate,
                unfocusedTrailingIconColor = Fsa.Slate,
                cursorColor = Fsa.Green,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun FilterRow(filters: Filters, onOpenFilters: () -> Unit, onChange: (Filters) -> Unit) {
    val count = listOf(filters.typeIds.isNotEmpty(), filters.minRating != null, filters.hideExempt, filters.hideAwaiting, filters.inspectedWithinMonths != null).count { it }
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PillChip(
            if (count > 0) stringResource(R.string.filters_count, count) else stringResource(R.string.filters),
            selected = count > 0,
            onClick = onOpenFilters,
            icon = Icons.Rounded.Tune,
        )
        PillChip(stringResource(R.string.filter_4_plus), selected = filters.minRating == 4, onClick = {
            onChange(filters.copy(minRating = if (filters.minRating == 4) null else 4))
        })
        PillChip(stringResource(R.string.filter_hide_exempt), selected = filters.hideExempt, onClick = { onChange(filters.copy(hideExempt = !filters.hideExempt)) })
        PillChip(stringResource(R.string.filter_recent), selected = filters.inspectedWithinMonths == 12, onClick = {
            onChange(filters.copy(inspectedWithinMonths = if (filters.inspectedWithinMonths == 12) null else 12))
        })
        PillChip(stringResource(R.string.filter_restaurants), selected = filters.typeIds == setOf(1), onClick = {
            onChange(filters.copy(typeIds = if (filters.typeIds == setOf(1)) emptySet() else setOf(1)))
        })
        PillChip(stringResource(R.string.filter_takeaways), selected = filters.typeIds == setOf(7844), onClick = {
            onChange(filters.copy(typeIds = if (filters.typeIds == setOf(7844)) emptySet() else setOf(7844)))
        })
    }
}

@Composable
private fun Results(
    state: NearbyState,
    miles: Boolean,
    onOpen: (Long) -> Unit,
    onOpenAddress: (String) -> Unit,
    onOpenUnlocated: (Int) -> Unit,
    onPlace: (io.github.sammeeeeeeee.higiene.data.Centre) -> Unit,
    onLocate: () -> Unit,
    onLocationSettings: () -> Unit,
    onDismissProblem: () -> Unit,
) {
    val listState = rememberLazyListState()
    // New results start at the top (otherwise the list keeps the footer's position as rows arrive).
    LaunchedEffect(state.rows.firstOrNull()?.listing?.est?.id, state.centre, state.filters, state.query) {
        listState.scrollToItem(0)
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        state.locationProblem?.let { problem ->
            item(key = "problem") {
                InfoCard(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(
                            when (problem) {
                                LocationProblem.NO_PERMISSION -> R.string.location_no_permission
                                LocationProblem.DISABLED -> R.string.location_disabled
                                LocationProblem.NO_FIX -> R.string.location_no_fix
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (problem == LocationProblem.DISABLED) TextLink(stringResource(R.string.open_location_settings), onLocationSettings)
                        else TextLink(stringResource(R.string.try_again), onLocate)
                        TextLink(stringResource(R.string.dismiss), onDismissProblem, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val place = state.place
        if (state.query.isNotBlank() && place != null) {
            item(key = "place") {
                InfoCard(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.postcode_found, place.label), style = MaterialTheme.typography.titleMedium)
                    if (place.approximate) Text(stringResource(R.string.postcode_approx), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    PrimaryPill(stringResource(R.string.show_near, place.label), onClick = { onPlace(place) }, icon = Icons.Rounded.TravelExplore)
                    state.fullPostcode?.let { pc ->
                        TextLink(stringResource(R.string.whos_cooking_at, place.label), onClick = { onOpenAddress(pc) })
                    }
                }
            }
        } else if (state.query.isNotBlank() && state.query.trim().length >= 2 && state.rows.isEmpty() && state.loaded) {
            item(key = "none") {
                Text(stringResource(R.string.no_search_results), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (state.query.isBlank() && state.centre == null && state.locationProblem == null) {
            item(key = "where") {
                InfoCard(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.where_body), style = MaterialTheme.typography.bodyMedium)
                    PrimaryPill(stringResource(R.string.use_my_location), onClick = onLocate, icon = Icons.Rounded.MyLocation)
                    Text(stringResource(R.string.where_or_postcode), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (state.query.isBlank() && state.centre != null && state.rows.isEmpty() && state.loaded) {
            item(key = "empty") {
                Text(stringResource(R.string.no_nearby_results), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.query.isNotBlank() && state.rows.isNotEmpty()) {
            item(key = "count") {
                Text(
                    pluralStringResource(R.plurals.search_results, state.rows.size, state.rows.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                )
            }
        }
        items(state.rows, key = { it.listing.est.id }) { row ->
            ListingRow(row, miles, onClick = { onOpen(row.listing.est.id) })
            Hairline(Modifier.padding(start = 72.dp))
        }
        state.unlocated?.let { u ->
            if (state.query.isBlank()) {
                item(key = "unlocated") {
                    InfoCard(Modifier.padding(16.dp)) {
                        Text(pluralStringResource(R.plurals.unlocated_title, u.count, u.count, u.la.name), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.unlocated_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextLink(stringResource(R.string.unlocated_show), onClick = { onOpenUnlocated(u.la.code) })
                    }
                }
            }
        }
        item(key = "footer") {
            Text(
                stringResource(R.string.list_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp).navigationBarsPadding(),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(filters: Filters, types: List<Pair<Int, String>>, onChange: (Filters) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.filters), style = LocalDisplay.current.section)

        Text(stringResource(R.string.sort_by), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                SortOrder.DISTANCE to R.string.sort_distance,
                SortOrder.RATING to R.string.sort_rating,
                SortOrder.RECENT to R.string.sort_recent,
                SortOrder.NAME to R.string.sort_name,
            ).forEach { (o, label) -> PillChip(stringResource(label), filters.sort == o, { onChange(filters.copy(sort = o)) }) }
        }

        Text(stringResource(R.string.min_rating), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip(stringResource(R.string.any), filters.minRating == null, { onChange(filters.copy(minRating = null)) })
            for (n in 1..5) {
                PillChip(if (n == 5) "5" else "$n+", filters.minRating == n, { onChange(filters.copy(minRating = n)) })
            }
        }
        Text(stringResource(R.string.min_rating_scotland), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Text(stringResource(R.string.inspected_within), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(null to R.string.any, 12 to R.string.within_1y, 24 to R.string.within_2y, 36 to R.string.within_3y).forEach { (m, label) ->
                PillChip(stringResource(label), filters.inspectedWithinMonths == m, { onChange(filters.copy(inspectedWithinMonths = m)) })
            }
        }

        SwitchRow(stringResource(R.string.filter_hide_exempt), filters.hideExempt) { onChange(filters.copy(hideExempt = it)) }
        SwitchRow(stringResource(R.string.filter_hide_awaiting), filters.hideAwaiting) { onChange(filters.copy(hideAwaiting = it)) }

        Text(stringResource(R.string.business_type), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip(stringResource(R.string.all_types), filters.typeIds.isEmpty(), { onChange(filters.copy(typeIds = emptySet())) })
            types.forEach { (id, name) ->
                val on = id in filters.typeIds
                PillChip(name, on, { onChange(filters.copy(typeIds = if (on) filters.typeIds - id else filters.typeIds + id)) })
            }
        }
        if (filters.active) TextLink(stringResource(R.string.clear_filters), onClick = { onChange(Filters(sort = filters.sort)) })
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Onboarding(refresh: RefreshUi, wifiOnly: Boolean, onStart: (anyNetwork: Boolean) -> Unit) {
    val hero = LocalHero.current
    Column(
        Modifier
            .fillMaxSize()
            .background(hero.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(Icons.Rounded.Storefront, contentDescription = null, tint = hero.accent, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.onboarding_title), style = LocalDisplay.current.hero, color = hero.headline)
        Text(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.bodyLarge, color = hero.body)
        listOf(R.string.onboarding_point_1, R.string.onboarding_point_2, R.string.onboarding_point_3, R.string.onboarding_point_4).forEach {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.padding(top = 8.dp).size(8.dp).background(hero.accent, CircleShape))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = hero.body)
            }
        }
        Spacer(Modifier.height(4.dp))
        when {
            refresh.running -> {
                val label = when (refresh.stage) {
                    "DOWNLOADING" -> refresh.fraction?.let { stringResource(R.string.progress_downloading_mb, refresh.done / 1_000_000, refresh.total / 1_000_000) }
                        ?: stringResource(R.string.progress_title)
                    "LOCATING" -> stringResource(R.string.progress_locating)
                    RefreshWorker.STAGE_POSTCODES -> stringResource(R.string.progress_postcodes)
                    else -> stringResource(R.string.progress_checking)
                }
                Text(label, style = MaterialTheme.typography.titleMedium, color = hero.body)
                val f = refresh.fraction
                if (f != null) LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth(), color = hero.accent, trackColor = Color.White.copy(alpha = 0.15f))
                else LinearProgressIndicator(Modifier.fillMaxWidth(), color = hero.accent, trackColor = Color.White.copy(alpha = 0.15f))
                Text(stringResource(R.string.onboarding_keep_open), style = MaterialTheme.typography.bodySmall, color = hero.body.copy(alpha = 0.8f))
            }
            refresh.waiting -> {
                Text(
                    stringResource(if (refresh.waitingForWifi) R.string.waiting_for_wifi else R.string.waiting_for_network),
                    style = MaterialTheme.typography.titleMedium,
                    color = hero.body,
                )
                if (refresh.waitingForWifi) TextLink(stringResource(R.string.use_mobile_data), onClick = { onStart(true) }, color = hero.accent)
            }
            else -> {
                refresh.failed?.let { Text(stringResource(R.string.download_failed, it), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyMedium) }
                PrimaryPill(stringResource(R.string.download_ratings), onClick = { onStart(false) }, modifier = Modifier.fillMaxWidth())
                if (wifiOnly) Text(stringResource(R.string.onboarding_wifi_note), style = MaterialTheme.typography.bodySmall, color = hero.body.copy(alpha = 0.8f))
                OutlinePill(stringResource(R.string.download_now_any_network), onClick = { onStart(true) }, onDark = true, modifier = Modifier.fillMaxWidth())
            }
        }
        Text(stringResource(R.string.not_endorsed), style = MaterialTheme.typography.bodySmall, color = hero.body.copy(alpha = 0.7f))
    }
}
