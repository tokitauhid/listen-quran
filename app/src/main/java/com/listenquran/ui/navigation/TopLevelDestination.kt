package com.listenquran.ui.navigation

/**
 * Top-level navigation destinations for the M3 Navigation Bar.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
) {
    HOME(route = "home", label = "Home"),
    SEARCH(route = "search", label = "Search"),
    LIBRARY(route = "library", label = "Your Library"),
    SETTINGS(route = "settings", label = "Settings"),
}
