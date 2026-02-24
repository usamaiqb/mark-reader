package com.markreader.ui.navigation

sealed class NavRoutes(val route: String) {
    data object Home : NavRoutes("home")
    data object Viewer : NavRoutes("viewer?uri={uri}") {
        fun createRoute(uri: String) = "viewer?uri=$uri"
    }
    data object Settings : NavRoutes("settings")
}
