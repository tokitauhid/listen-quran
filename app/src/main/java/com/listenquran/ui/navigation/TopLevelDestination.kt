package com.listenquran.ui.navigation

/**
 * Top-level navigation destinations for the M3 Navigation Bar.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
) {
    HOME(route = "home", label = "Home"),
    RECITERS(route = "reciters", label = "Reciters"),
    SEARCH(route = "search", label = "Search"),
    LIBRARY(route = "library", label = "Library"),
    SETTINGS(route = "settings", label = "Settings"),
}
