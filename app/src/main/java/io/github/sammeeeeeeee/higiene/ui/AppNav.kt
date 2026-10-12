package io.github.sammeeeeeeee.higiene.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.activity.BackEventCompat
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import io.github.sammeeeeeeee.higiene.ui.theme.LocalIsDark
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.ui.address.ListScreen
import io.github.sammeeeeeeee.higiene.ui.components.PillItem
import io.github.sammeeeeeeee.higiene.ui.components.SegmentedPills
import io.github.sammeeeeeeee.higiene.ui.detail.DetailScreen
import io.github.sammeeeeeeee.higiene.ui.favourites.FavouritesScreen
import io.github.sammeeeeeeee.higiene.ui.nearby.NearbyScreen
import io.github.sammeeeeeeee.higiene.ui.settings.LicencesScreen
import io.github.sammeeeeeeee.higiene.ui.settings.SettingsScreen
import io.github.sammeeeeeeee.higiene.ui.theme.Fsa

/** Space lists leave at the bottom so their last item clears the floating navigation pill. */
val LocalBottomSpace = compositionLocalOf { 0.dp }

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Shares an element (e.g. a business logo) between the list and the detail page. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val scope = LocalNavScope.current ?: return this
    return with(shared) {
        this@sharedElement.sharedElement(
            rememberSharedContentState(key),
            scope,
            // Same length as the back swipe, and the logo stays put for the first half: while the page is
            // only being dragged it stays on the page, and it flies home only as the page leaves.
            boundsTransform = { from, _ ->
                keyframes {
                    durationMillis = PREDICTIVE_MS
                    from at PREDICTIVE_MS / 2 using FastOutSlowInEasing
                }
            },
        )
    }
}

private data class Tab(val route: String, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("nearby", R.string.tab_nearby, Icons.Rounded.NearMe),
    Tab("favourites", R.string.tab_favourites, Icons.Rounded.Favorite),
    Tab("settings", R.string.tab_settings, Icons.Rounded.Tune),
)

private val NAV_HEIGHT = 68.dp
private const val PREDICTIVE_MS = 340
private val NAV_MARGIN = 14.dp

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AppNav(openEstablishment: Long?, onOpenedEstablishment: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTab = route in tabs.map { it.route }

    // Wait for the NavHost to set its graph (entry != null) before following a notification.
    LaunchedEffect(openEstablishment, entry != null) {
        if (openEstablishment != null && entry != null) {
            nav.navigate("detail/$openEstablishment")
            onOpenedEstablishment()
        }
    }

    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomSpace: Dp = if (onTab) NAV_HEIGHT + NAV_MARGIN * 2 + navInset else navInset

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    Box(Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            CompositionLocalProvider(LocalSharedScope provides this, LocalBottomSpace provides bottomSpace) {
                NavHost(
                    nav,
                    startDestination = "nearby",
                    enterTransition = { fadeIn(tween(260)) + slideInHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = IntOffset.VisibilityThreshold)) { it / 6 } },
                    exitTransition = { fadeOut(tween(180)) },
                    popEnterTransition = { fadeIn(tween(260)) + scaleIn(initialScale = 0.96f) },
                    popExitTransition = { fadeOut(tween(200)) + slideOutHorizontally(spring(dampingRatio = 0.9f, stiffness = 500f, visibilityThreshold = IntOffset.VisibilityThreshold)) { it / 6 } },
                    // Back swipe: the page shrinks and drifts with the finger, stays solid while dragged, then
                    // fades as it finishes. All parts share one length (PREDICTIVE_MS), so swipe progress maps
                    // evenly onto the animation. The library default scaled to 70% without fading, which left an
                    // opaque card hanging over the list before it vanished.
                    predictivePopEnterTransition = { scaleIn(tween(PREDICTIVE_MS, easing = FastOutSlowInEasing), initialScale = 0.95f) },
                    predictivePopExitTransition = { edge ->
                        val towards = if (edge == BackEventCompat.EDGE_RIGHT) -1 else 1
                        fadeOut(keyframes { durationMillis = PREDICTIVE_MS; 1f at PREDICTIVE_MS / 2 }) +
                            scaleOut(tween(PREDICTIVE_MS, easing = FastOutSlowInEasing), targetScale = 0.9f) +
                            slideOutHorizontally(tween(PREDICTIVE_MS, easing = FastOutSlowInEasing)) { w -> towards * w / 16 }
                    },
                ) {
                    val open: (Long) -> Unit = { id -> nav.navigate("detail/$id") }
                    val openAddress: (String) -> Unit = { pc -> nav.navigate("address/$pc") }
                    tab("nearby") {
                        NearbyScreen(onOpen = open, onOpenAddress = openAddress, onOpenUnlocated = { la -> nav.navigate("unlocated/$la") })
                    }
                    tab("favourites") { FavouritesScreen(onOpen = open) }
                    tab("settings") { SettingsScreen(onLicences = { nav.navigate("licences") }) }
                    // Back only from the resumed screen, so a quick double tap can't pop the start page.
                    fun back(entry: NavBackStackEntry): () -> Unit = {
                        if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) nav.popBackStack()
                    }
                    screen("licences") { LicencesScreen(onBack = back(it)) }
                    screen("detail/{id}", listOf(navArgument("id") { type = NavType.LongType })) {
                        DetailScreen(onBack = back(it), onOpen = open, onOpenAddress = openAddress)
                    }
                    screen("address/{pc}", listOf(navArgument("pc") { type = NavType.StringType })) {
                        ListScreen(onBack = back(it), onOpen = open)
                    }
                    screen("unlocated/{la}", listOf(navArgument("la") { type = NavType.IntType })) {
                        ListScreen(onBack = back(it), onOpen = open)
                    }
                }
            }
        }

        // Soft fade behind the floating pill so list content doesn't clash with it.
        AnimatedVisibility(onTab, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(NAV_HEIGHT + NAV_MARGIN * 2 + navInset + 24.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = 0.92f)))),
            )
        }
        AnimatedVisibility(
            onTab,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = 400f)) { it } + fadeIn(),
            exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
        ) {
            val selected = tabs.indexOfFirst { it.route == route }.coerceAtLeast(0)
            SegmentedPills(
                items = tabs.map { PillItem(stringResource(it.label), it.icon) },
                selected = selected,
                onSelect = { i ->
                    nav.navigate(tabs[i].route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                height = NAV_HEIGHT,
                track = if (LocalIsDark.current) Color(0xFF123F3E) else Fsa.Ink,
                indicator = Fsa.Lime,
                selectedContent = Fsa.Ink,
                content = Color.White.copy(alpha = 0.78f),
                vertical = true,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = NAV_MARGIN)
                    .fillMaxWidth()
                    .shadow(20.dp, CircleShape, ambientColor = Color.Black, spotColor = Color.Black)
                    .border(1.dp, Color.White.copy(alpha = if (LocalIsDark.current) 0.08f else 0f), CircleShape),
            )
        }
    }
    }
}

private fun NavGraphBuilder.tab(route: String, content: @Composable () -> Unit) {
    val isTab: (NavBackStackEntry) -> Boolean = { e -> tabs.any { it.route == e.destination.route } }
    composable(
        route,
        enterTransition = { fadeIn(tween(220)) },
        exitTransition = { fadeOut(tween(160)) },
        // Back from a page: the list comes forward under it, without fading in from blank.
        popEnterTransition = {
            if (isTab(initialState)) fadeIn(tween(220)) else scaleIn(tween(PREDICTIVE_MS, easing = FastOutSlowInEasing), initialScale = 0.95f)
        },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        CompositionLocalProvider(LocalNavScope provides this) { content() }
    }
}

private fun NavGraphBuilder.screen(route: String, arguments: List<NamedNavArgument> = emptyList(), content: @Composable (NavBackStackEntry) -> Unit) {
    composable(route, arguments = arguments) { entry ->
        // Round the page's corners as it leaves, so a back swipe looks like a card being put away.
        val corner by transition.animateDp({ tween(PREDICTIVE_MS, easing = FastOutSlowInEasing) }, label = "corner") {
            if (it == EnterExitState.PostExit) 32.dp else 0.dp
        }
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(corner))) {
            CompositionLocalProvider(LocalNavScope provides this@composable) { content(entry) }
        }
    }
}
