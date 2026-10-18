package com.listenquran.ui.navigation

import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/**
 * Top-level navigation destinations for the M3 Navigation Bar with nested graph routes.
 */
enum class TopLevelDestination(
    val route: String,
    val graphRoute: String,
    val label: String,
) {
    HOME(route = "home", graphRoute = "home_graph", label = "Home"),
    RECITERS(route = "reciters", graphRoute = "reciters_graph", label = "Reciters"),
    SEARCH(route = "search", graphRoute = "home_graph", label = "Search"),
    LIBRARY(route = "library", graphRoute = "library_graph", label = "Library"),
    SETTINGS(route = "settings", graphRoute = "home_graph", label = "Settings"),
    SURAHS(route = "surahs", graphRoute = "home_graph", label = "Surahs"),
    RADIO(route = "radio", graphRoute = "home_graph", label = "Radio");

    val startRoute: String get() = route

    companion object {
        val bottomNavDestinations = listOf(HOME, RECITERS, LIBRARY)

        /**
         * Resolves the corresponding bottom navigation tab for any destination route or sub-page.
         */
        fun fromRoute(route: String?): TopLevelDestination {
            return when {
                route == null -> HOME
                route == SETTINGS.route -> HOME
                route == SEARCH.route -> HOME
                route == SURAHS.route -> HOME
                route == RADIO.route -> HOME
                route.startsWith("home") -> HOME
                route.startsWith("reciter") -> RECITERS
                route.startsWith("library") || route.startsWith("playlist") -> LIBRARY
                else -> HOME
            }
        }
    }
}

/**
 * Single reusable tab navigation helper for bottom bar destinations.
 *
 * Rules:
 * 1. Tapping the currently selected tab (even while on a sub-page) pops back to that tab's root.
 * 2. Tapping a different tab navigates to that tab's root screen.
 * 3. Does not restore stale secondary pages on top of tab roots.
 */
fun NavHostController.navigateToTab(
    destination: TopLevelDestination,
    onResetTabRoot: () -> Unit = {},
) {
    val currentDestination = currentBackStackEntry?.destination
    val isCurrentTab = currentDestination?.hierarchy?.any {
        it.route == destination.graphRoute || it.route == destination.route
    } == true

    if (isCurrentTab) {
        // Already on this tab or one of its sub-pages: pop back to root
        popBackStack(destination.route, inclusive = false)
        onResetTabRoot()
    } else {
        // Switching tabs: navigate to target tab's graph root directly
        navigate(destination.graphRoute) {
            popUpTo(graph.findStartDestination().id) {
                saveState = false
            }
            launchSingleTop = true
            restoreState = false
        }
        popBackStack(destination.route, inclusive = false)
        onResetTabRoot()
    }
}

/**
 * Single reusable helper for secondary page navigation on top of the current tab.
 */
fun NavHostController.navigateToDetail(route: String) {
    navigate(route) {
        launchSingleTop = true
    }
}
