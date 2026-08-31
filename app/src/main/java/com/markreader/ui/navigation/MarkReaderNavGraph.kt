package com.markreader.ui.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
import com.markreader.ui.screens.EditorScreen
import com.markreader.ui.screens.HomeScreen
import com.markreader.ui.screens.SettingsScreen
import com.markreader.ui.screens.ViewerScreen

fun NavGraphBuilder.markReaderNavGraph(
    navController: NavController,
    externalUri: String?
) {
    composable(NavRoutes.Home.route) {
        HomeScreen(
            onOpenSettings = { navController.navigateToSettings() },
            onOpenViewer = { uri ->
                navController.navigate(NavRoutes.Viewer.createRoute(uri)) {
                    popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
                    launchSingleTop = true
                }
            },
            onOpenEditor = { uri ->
                navController.navigate(NavRoutes.Editor.createRoute(uri))
            }
        )
    }

    composable(
        route = NavRoutes.Viewer.route,
        arguments = listOf(navArgument("uri") { nullable = true })
    ) { backStackEntry ->
        val uri = backStackEntry.arguments?.getString("uri")
            ?.takeIf { it.isNotBlank() }
            ?: externalUri
        val fileSaved by backStackEntry.savedStateHandle
            .getStateFlow("file_saved", false)
            .collectAsStateWithLifecycle()
        ViewerScreen(
            onOpenSettings = { navController.navigateToSettings() },
            onOpenEditor = { editorUri ->
                navController.navigate(NavRoutes.Editor.createRoute(editorUri))
            },
            uriString = uri,
            fileSaved = fileSaved,
            onFileSavedConsumed = { backStackEntry.savedStateHandle["file_saved"] = false }
        )
    }

    composable(NavRoutes.Settings.route) {
        SettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }

    composable(
        route = NavRoutes.Editor.route,
        arguments = listOf(navArgument("uri") { nullable = true })
    ) { backStackEntry ->
        val uri = backStackEntry.arguments?.getString("uri")
        EditorScreen(
            uriString = uri,
            onNavigateBack = { navController.popBackStack() },
            onFileSaved = {
                navController.previousBackStackEntry?.savedStateHandle?.set("file_saved", true)
            }
        )
    }
}

private fun NavController.navigateToSettings() {
    navigate(NavRoutes.Settings.route) {
        launchSingleTop = true
    }
}
