package io.github.sammeeeeeeee.higiene.ui.nearby

import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import android.Manifest
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.WhereToVote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.data.Centre
import io.github.sammeeeeeeee.higiene.data.Filters
import io.github.sammeeeeeeee.higiene.data.SortOrder
import io.github.sammeeeeeeee.higiene.location.LocationHelper
import io.github.sammeeeeeeee.higiene.ui.LocalBottomSpace
import io.github.sammeeeeeeee.higiene.ui.RefreshUi
import io.github.sammeeeeeeee.higiene.ui.components.DarkCard
import io.github.sammeeeeeeee.higiene.ui.components.HeroHeader
import io.github.sammeeeeeeee.higiene.ui.components.HeroIconButton
import io.github.sammeeeeeeee.higiene.ui.components.InfoCard
import io.github.sammeeeeeeee.higiene.ui.components.OutlinePill
import io.github.sammeeeeeeee.higiene.ui.components.PillChip
import io.github.sammeeeeeeee.higiene.ui.components.PillItem
import io.github.sammeeeeeeee.higiene.ui.components.PrimaryPill
import io.github.sammeeeeeeee.higiene.ui.components.Reveal
import io.github.sammeeeeeeee.higiene.ui.components.SegmentedPills
import io.github.sammeeeeeeee.higiene.ui.components.SkeletonTiles
import io.github.sammeeeeeeee.higiene.ui.components.TextLink
import io.github.sammeeeeeeee.higiene.ui.components.tactileClick
import io.github.sammeeeeeeee.higiene.ui.theme.Fsa
import io.github.sammeeeeeeee.higiene.ui.pretty
import io.github.sammeeeeeeee.higiene.ui.typeIcon
import io.github.sammeeeeeeee.higiene.ui.typeLabel
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
        if (granted.values.any { it }) vm.useMyLocation() else vm.permissionDenied()
    }
    val locate = {
        if (LocationHelper.hasPermission(context)) vm.useMyLocation()
        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }

    AnimatedContent(state.hasData, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nearby") { has ->
        when (has) {
            null -> Box(Modifier.fillMaxSize())
            false -> Onboarding(refresh, wifiOnly = settings.wifiOnly, onStart = { vm.startDownload(anyNetwork = it) })
            true -> Column(Modifier.fillMaxSize()) {
                HeroHeader(
                    title = when {
                        state.centre == null -> stringResource(R.string.nearby_title_where)
                        state.centre!!.fromGps -> stringResource(R.string.nearby_title_near_you)
                        else -> stringResource(R.string.nearby_title_near, state.centre!!.label)
                    },
                    actions = {
                        if (state.locating) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(22.dp), color = Fsa.Lime, strokeWidth = 2.5.dp)
                            }
                        } else {
                            HeroIconButton(Icons.Rounded.MyLocation, stringResource(R.string.use_my_location), locate, LocalHero.current.body)
                        }
                    },
                    bottomPadding = 20,
                ) {
                    SearchField(state.query, vm::setQuery)
                    when {
                        refresh.running -> RefreshStrip(refresh)
                        (refresh.waiting || refresh.retrying) && refresh.waitingForWifi -> Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.waiting_for_wifi), style = MaterialTheme.typography.labelSmall, color = LocalHero.current.body.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                            TextLink(stringResource(R.string.use_mobile_data), onClick = { vm.useMobileData() }, color = Fsa.Lime)
                        }
                    }
                }
                FilterRow(state.filters, onOpenFilters = { showFilters = true }, onChange = vm::setFilters)
                // Pull down to check for new ratings (and find your position again if the list follows it).
                // "starting" covers the moment before the check begins, so the spinner doesn't blink off;
                // "following" keeps it for this pull only, not for background updates.
                var starting by remember { mutableStateOf(false) }
                var following by remember { mutableStateOf(false) }
                val busy = state.locating || refresh.running
                LaunchedEffect(busy, starting) { if (!busy && !starting) following = false }
                val scope = rememberCoroutineScope()
                androidx.compose.material3.pulltorefresh.PullToRefreshBox(
                    isRefreshing = starting || (following && busy),
                    onRefresh = {
                        starting = true
                        following = true
                        vm.pullRefresh()
                        scope.launch {
                            kotlinx.coroutines.delay(1200)
                            starting = false
                        }
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Results(
                        state = state,
                        miles = settings.miles,
                        onOpen = onOpen,
                        onOpenAddress = onOpenAddress,
                        onOpenUnlocated = onOpenUnlocated,
                        onPlace = vm::setCentre,
                        onLocate = locate,
                        onLocationSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } },
                        onAppSettings = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null)))
                            }
                        },
                        onDismissProblem = vm::dismissLocationProblem,
                    )
                }
            }
        }
    }

    if (showFilters) {
        ModalBottomSheet(
            onDismissRequest = { showFilters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            FiltersSheet(state.filters, state.types.map { it.id to it.name }, onChange = vm::setFilters)
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    val dark = io.github.sammeeeeeeee.higiene.ui.theme.LocalIsDark.current
    val field = if (dark) Color(0xFF123F3E) else Color.White
    val text = if (dark) Color(0xFFE6EEEC) else Fsa.Obsidian
    val hint = if (dark) Color(0xFF9FB3B0) else Fsa.Slate
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
        textStyle = MaterialTheme.typography.bodyLarge,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = field,
            unfocusedContainerColor = field,
            focusedTextColor = text,
            unfocusedTextColor = text,
            focusedPlaceholderColor = hint,
            unfocusedPlaceholderColor = hint,
            focusedLeadingIconColor = if (dark) Fsa.Lime else Fsa.Ink,
            unfocusedLeadingIconColor = hint,
            focusedTrailingIconColor = hint,
            unfocusedTrailingIconColor = hint,
            cursorColor = if (dark) Fsa.Lime else Fsa.Green,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}

/** Slim progress pill inside the hero while a background update runs. */
@Composable
private fun RefreshStrip(refresh: RefreshUi) {
    val hero = LocalHero.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stageLabel(refresh), style = MaterialTheme.typography.labelSmall, color = hero.body.copy(alpha = 0.85f))
        PillProgress(refresh.fraction, height = 6.dp)
    }
}

@Composable
private fun stageLabel(refresh: RefreshUi): String = when (refresh.stage) {
    "DOWNLOADING" -> if (refresh.total > 0) stringResource(R.string.progress_downloading_mb, refresh.done / 1_000_000, refresh.total / 1_000_000) else stringResource(R.string.progress_title)
    "IMPORTING" -> stringResource(R.string.progress_importing)
    "LOCATING" -> stringResource(R.string.progress_locating)
    RefreshWorker.STAGE_POSTCODES -> stringResource(R.string.progress_postcodes)
    else -> stringResource(R.string.progress_checking)
}

/** Thick rounded progress track with a springy bright fill. Null fraction shows a third. */
@Composable
fun PillProgress(fraction: Float?, modifier: Modifier = Modifier, height: Dp = 14.dp) {
    val animated by animateFloatAsState(fraction ?: 0.33f, spring(dampingRatio = 0.9f, stiffness = 120f), label = "progress")
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.14f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated.coerceIn(0.02f, 1f))
                .clip(CircleShape)
                .background(Fsa.Lime),
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
        PillChip(stringResource(R.string.filter_restaurants), selected = filters.typeIds == setOf(1), onClick = {
            onChange(filters.copy(typeIds = if (filters.typeIds == setOf(1)) emptySet() else setOf(1)))
        })
        PillChip(stringResource(R.string.filter_takeaways), selected = filters.typeIds == setOf(7844), onClick = {
            onChange(filters.copy(typeIds = if (filters.typeIds == setOf(7844)) emptySet() else setOf(7844)))
        })
        PillChip(stringResource(R.string.filter_hide_exempt), selected = filters.hideExempt, onClick = { onChange(filters.copy(hideExempt = !filters.hideExempt)) })
        PillChip(stringResource(R.string.filter_recent), selected = filters.inspectedWithinMonths == 12, onClick = {
            onChange(filters.copy(inspectedWithinMonths = if (filters.inspectedWithinMonths == 12) null else 12))
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
    onPlace: (Centre) -> Unit,
    onLocate: () -> Unit,
    onLocationSettings: () -> Unit,
    onAppSettings: () -> Unit,
    onDismissProblem: () -> Unit,
) {
    val listState = rememberLazyListState()
    // New results start at the top (otherwise the list keeps the footer's position as rows arrive),
    // but coming back to the same results keeps the user's place.
    val resultsKey = listOf(state.rows.firstOrNull()?.listing?.est?.id, state.centre, state.filters, state.query).hashCode()
    var shownKey by rememberSaveable { mutableStateOf<Int?>(null) }
    LaunchedEffect(resultsKey) {
        if (shownKey != null && shownKey != resultsKey) listState.scrollToItem(0)
        shownKey = resultsKey
    }
    if (state.loading && state.rows.isEmpty() && state.place == null) {
        SkeletonTiles()
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = LocalBottomSpace.current + 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.locationProblem?.let { problem ->
            item(key = "problem") {
                InfoCard {
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
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (problem) {
                            LocationProblem.DISABLED -> TextLink(stringResource(R.string.open_location_settings), onLocationSettings)
                            LocationProblem.NO_PERMISSION -> TextLink(stringResource(R.string.open_app_settings), onAppSettings)
                            LocationProblem.NO_FIX -> TextLink(stringResource(R.string.try_again), onLocate)
                        }
                        TextLink(stringResource(R.string.dismiss), onDismissProblem, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        val place = state.place
        if (state.query.isNotBlank() && place != null) {
            item(key = "place") {
                DarkCard(stringResource(R.string.postcode_found, place.label)) {
                    if (place.approximate) Text(stringResource(R.string.postcode_approx), style = MaterialTheme.typography.bodySmall)
                    PrimaryPill(stringResource(R.string.show_near, place.label), onClick = { onPlace(place) }, icon = Icons.Rounded.TravelExplore)
                    state.fullPostcode?.let { pc ->
                        TextLink(stringResource(R.string.whos_cooking_at, place.label), onClick = { onOpenAddress(pc) }, color = Fsa.Lime)
                    }
                }
            }
        } else if (state.query.isNotBlank() && state.query.trim().length >= 2 && state.rows.isEmpty() && state.loaded) {
            item(key = "none") { EmptyNote(stringResource(R.string.no_search_results)) }
        } else if (state.query.isBlank() && state.centre == null && state.locationProblem == null) {
            item(key = "where") {
                DarkCard(stringResource(R.string.where_title)) {
                    Text(stringResource(R.string.where_body), style = MaterialTheme.typography.bodyMedium)
                    PrimaryPill(stringResource(R.string.use_my_location), onClick = onLocate, icon = Icons.Rounded.MyLocation)
                    Text(stringResource(R.string.where_or_postcode), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else if (state.query.isBlank() && state.centre != null && state.rows.isEmpty() && state.loaded) {
            item(key = "empty") { EmptyNote(stringResource(R.string.no_nearby_results)) }
        }
        if (state.query.isNotBlank() && state.rows.isNotEmpty()) {
            item(key = "count") {
                Text(
                    pluralStringResource(R.plurals.search_results, state.rows.size, state.rows.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
        items(state.rows, key = { it.listing.est.id }) { row ->
            ListingRow(row, miles, onClick = { onOpen(row.listing.est.id) }, modifier = Modifier.animateItem())
        }
        state.unlocated?.let { u ->
            if (state.query.isBlank()) {
                item(key = "unlocated") {
                    InfoCard(Modifier.padding(top = 6.dp)) {
                        Icon(Icons.Rounded.WhereToVote, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        Text(pluralStringResource(R.plurals.unlocated_title, u.count, u.count, u.la.name), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.unlocated_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinePill(stringResource(R.string.unlocated_show), onClick = { onOpenUnlocated(u.la.code) })
                    }
                }
            }
        }
        item(key = "footer") {
            Text(
                stringResource(
                    R.string.list_footer,
                    state.dataCheckedAt?.let { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate().pretty() } ?: "—",
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 24.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FiltersSheet(filters: Filters, types: List<Pair<Int, String>>, onChange: (Filters) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(stringResource(R.string.filters).uppercase(), style = LocalDisplay.current.section)

        SheetLabel(stringResource(R.string.sort_by))
        val sorts = listOf(SortOrder.DISTANCE to R.string.sort_distance, SortOrder.RATING to R.string.sort_rating, SortOrder.RECENT to R.string.sort_recent_short, SortOrder.NAME to R.string.sort_name)
        SegmentedPills(
            items = sorts.map { PillItem(stringResource(it.second)) },
            selected = sorts.indexOfFirst { it.first == filters.sort },
            onSelect = { onChange(filters.copy(sort = sorts[it].first)) },
            modifier = Modifier.fillMaxWidth(),
        )

        SheetLabel(stringResource(R.string.min_rating))
        val mins = listOf<Int?>(null, 1, 2, 3, 4, 5)
        SegmentedPills(
            items = mins.map { PillItem(it?.let { n -> if (n == 5) "5" else "$n+" } ?: stringResource(R.string.any)) },
            selected = mins.indexOf(filters.minRating).coerceAtLeast(0),
            onSelect = { onChange(filters.copy(minRating = mins[it])) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.min_rating_scotland), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SheetLabel(stringResource(R.string.inspected_within))
        val within = listOf(null to R.string.any, 12 to R.string.within_1y, 24 to R.string.within_2y, 36 to R.string.within_3y)
        SegmentedPills(
            items = within.map { PillItem(stringResource(it.second)) },
            selected = within.indexOfFirst { it.first == filters.inspectedWithinMonths }.coerceAtLeast(0),
            onSelect = { onChange(filters.copy(inspectedWithinMonths = within[it].first)) },
            modifier = Modifier.fillMaxWidth(),
        )

        SwitchRow(stringResource(R.string.filter_hide_exempt), filters.hideExempt) { onChange(filters.copy(hideExempt = it)) }
        SwitchRow(stringResource(R.string.filter_hide_awaiting), filters.hideAwaiting) { onChange(filters.copy(hideAwaiting = it)) }

        SheetLabel(stringResource(R.string.business_type))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillChip(stringResource(R.string.all_types), filters.typeIds.isEmpty(), { onChange(filters.copy(typeIds = emptySet())) })
            val context = LocalContext.current
            types.sortedBy { typeLabel(context, it.first, it.second) }.forEach { (id, name) ->
                val on = id in filters.typeIds
                PillChip(typeLabel(context, id, name), on, { onChange(filters.copy(typeIds = if (on) filters.typeIds - id else filters.typeIds + id)) }, icon = typeIcon(id))
            }
        }
        if (filters.active) TextLink(stringResource(R.string.clear_filters), onClick = { onChange(Filters(sort = filters.sort)) })
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
}

/** Label + bright pill switch; the whole row is the touch target. */
@Composable
fun SwitchRow(label: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val toggle = {
        haptics.performHapticFeedback(if (checked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
        onChange(!checked)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .tactileClick(pressed = 0.98f, haptic = null, role = Role.Switch, onClick = toggle)
            .semantics(mergeDescendants = true) { toggleableState = ToggleableState(checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            thumbContent = if (checked) {
                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
            } else {
                null
            },
            colors = SwitchDefaults.colors(
                checkedTrackColor = Fsa.Lime,
                checkedThumbColor = Fsa.Ink,
                checkedIconColor = Fsa.Lime,
                checkedBorderColor = Fsa.Lime,
            ),
        )
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
            .padding(horizontal = 24.dp)
            .padding(top = 28.dp, bottom = LocalBottomSpace.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Reveal(0) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Fsa.Lime), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Fsa.Ink, modifier = Modifier.size(32.dp))
            }
        }
        Reveal(1) { Text(stringResource(R.string.onboarding_title), style = LocalDisplay.current.hero, color = hero.headline) }
        Reveal(2) { Text(stringResource(R.string.onboarding_body), style = MaterialTheme.typography.titleLarge, color = hero.body) }
        val points = listOf(
            Icons.Rounded.CloudDownload to R.string.onboarding_point_1,
            Icons.Rounded.Lock to R.string.onboarding_point_2,
            Icons.Rounded.Public to R.string.onboarding_point_3,
            Icons.Rounded.WhereToVote to R.string.onboarding_point_4,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            points.forEachIndexed { i, (icon, text) ->
                Reveal(3 + i) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Fsa.Lime.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = Fsa.Lime, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(stringResource(text), style = MaterialTheme.typography.bodyMedium, color = hero.body)
                    }
                }
            }
        }
        Reveal(7) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    refresh.running -> {
                        val f = refresh.fraction
                        Text(
                            if (f != null && (refresh.stage == "DOWNLOADING" || refresh.stage == "IMPORTING")) "${(f * 100).toInt()}%" else "…",
                            style = LocalDisplay.current.hero,
                            color = hero.headline,
                        )
                        Text(stageLabel(refresh), style = MaterialTheme.typography.titleMedium, color = hero.body)
                        PillProgress(if (refresh.stage == "DOWNLOADING" || refresh.stage == "IMPORTING") f else null)
                        Text(stringResource(R.string.onboarding_keep_open), style = MaterialTheme.typography.bodySmall, color = hero.body.copy(alpha = 0.8f))
                    }
                    refresh.retrying -> {
                        refresh.failed?.let { Text(stringResource(R.string.download_failed, it), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyMedium) }
                        Text(stringResource(R.string.retrying), style = LocalDisplay.current.section, color = hero.headline)
                        PillProgress(null)
                        if (refresh.waitingForWifi) OutlinePill(stringResource(R.string.use_mobile_data), onClick = { onStart(true) }, onDark = true, modifier = Modifier.fillMaxWidth())
                    }
                    refresh.waiting -> {
                        Text(
                            stringResource(if (refresh.waitingForWifi) R.string.waiting_for_wifi else R.string.waiting_for_network),
                            style = LocalDisplay.current.section,
                            color = hero.headline,
                        )
                        PillProgress(null)
                        if (refresh.waitingForWifi) OutlinePill(stringResource(R.string.use_mobile_data), onClick = { onStart(true) }, onDark = true, modifier = Modifier.fillMaxWidth())
                    }
                    else -> {
                        refresh.failed?.let { Text(stringResource(R.string.download_failed, it), color = Color(0xFFFFB4AB), style = MaterialTheme.typography.bodyMedium) }
                        PrimaryPill(stringResource(R.string.download_ratings), onClick = { onStart(false) }, modifier = Modifier.fillMaxWidth())
                        if (wifiOnly) {
                            Text(
                                stringResource(R.string.onboarding_wifi_note),
                                style = MaterialTheme.typography.bodySmall,
                                color = hero.body.copy(alpha = 0.8f),
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                            )
                            OutlinePill(stringResource(R.string.download_now_any_network), onClick = { onStart(true) }, onDark = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        Reveal(8) { Text(stringResource(R.string.not_endorsed), style = MaterialTheme.typography.bodySmall, color = hero.body.copy(alpha = 0.65f)) }
        Spacer(Modifier.height(8.dp))
    }
}
