package com.markreader.ui.navigation

import android.net.Uri

sealed class NavRoutes(val route: String) {
    data object Home : NavRoutes("home")
    data object Viewer : NavRoutes("viewer?uri={uri}") {
        fun createRoute(uri: String) = "viewer?uri=${Uri.encode(uri)}"
    }
    data object Settings : NavRoutes("settings")
    data object Editor : NavRoutes("editor?uri={uri}&isMarkdown={isMarkdown}") {
        fun createRoute(uri: String, isMarkdown: Boolean) =
            "editor?uri=${Uri.encode(uri)}&isMarkdown=$isMarkdown"
    }
}
