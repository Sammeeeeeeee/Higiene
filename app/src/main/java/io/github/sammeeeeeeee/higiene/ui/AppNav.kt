package io.github.sammeeeeeeee.higiene.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.sammeeeeeeee.higiene.R
import io.github.sammeeeeeeee.higiene.ui.address.ListScreen
import io.github.sammeeeeeeee.higiene.ui.detail.DetailScreen
import io.github.sammeeeeeeee.higiene.ui.favourites.FavouritesScreen
import io.github.sammeeeeeeee.higiene.ui.nearby.NearbyScreen
import io.github.sammeeeeeeee.higiene.ui.settings.LicencesScreen
import io.github.sammeeeeeeee.higiene.ui.settings.SettingsScreen
import io.github.sammeeeeeeee.higiene.ui.theme.Fsa

private data class Tab(val route: String, val label: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("nearby", R.string.tab_nearby, Icons.Rounded.NearMe),
    Tab("favourites", R.string.tab_favourites, Icons.Rounded.Favorite),
    Tab("settings", R.string.tab_settings, Icons.Rounded.Settings),
)

@Composable
fun AppNav(openEstablishment: Long?, onOpenedEstablishment: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route

    // Wait for the NavHost to set its graph (entry != null) before following a notification.
    LaunchedEffect(openEstablishment, entry != null) {
        if (openEstablishment != null && entry != null) {
            nav.navigate("detail/$openEstablishment")
            onOpenedEstablishment()
        }
    }

    Scaffold(
        bottomBar = {
            if (route in tabs.map { it.route }) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.label)) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = Fsa.Lime,
                                selectedIconColor = Fsa.Ink,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()).consumeWindowInsets(padding)) {
            NavHost(nav, startDestination = "nearby") {
                val open: (Long) -> Unit = { id -> nav.navigate("detail/$id") }
                val openAddress: (String) -> Unit = { pc -> nav.navigate("address/$pc") }
                composable("nearby") {
                    NearbyScreen(onOpen = open, onOpenAddress = openAddress, onOpenUnlocated = { la -> nav.navigate("unlocated/$la") })
                }
                composable("favourites") { FavouritesScreen(onOpen = open) }
                composable("settings") { SettingsScreen(onLicences = { nav.navigate("licences") }) }
                composable("licences") { LicencesScreen(onBack = { nav.popBackStack() }) }
                composable("detail/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) {
                    DetailScreen(onBack = { nav.popBackStack() }, onOpen = open, onOpenAddress = openAddress)
                }
                composable("address/{pc}", arguments = listOf(navArgument("pc") { type = NavType.StringType })) {
                    ListScreen(onBack = { nav.popBackStack() }, onOpen = open)
                }
                composable("unlocated/{la}", arguments = listOf(navArgument("la") { type = NavType.IntType })) {
                    ListScreen(onBack = { nav.popBackStack() }, onOpen = open)
                }
            }
        }
    }
}
