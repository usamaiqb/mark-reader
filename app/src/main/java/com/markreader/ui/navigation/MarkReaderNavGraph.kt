package com.markreader.ui.navigation

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
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
                val encoded = Uri.encode(uri)
                navController.navigate(NavRoutes.Viewer.createRoute(encoded))
            },
            onOpenEditor = { uri, isMarkdown ->
                val encoded = Uri.encode(uri)
                navController.navigate(NavRoutes.Editor.createRoute(encoded, isMarkdown))
            }
        )
    }

    composable(
        route = NavRoutes.Viewer.route,
        arguments = listOf(navArgument("uri") { nullable = true })
    ) { backStackEntry ->
        val uri = backStackEntry.arguments?.getString("uri") ?: externalUri
        val fileSaved by backStackEntry.savedStateHandle
            .getStateFlow("file_saved", false)
            .collectAsStateWithLifecycle()
        ViewerScreen(
            onOpenSettings = { navController.navigateToSettings() },
            onOpenEditor = { editorUri, isMarkdown ->
                val encoded = Uri.encode(editorUri)
                navController.navigate(NavRoutes.Editor.createRoute(encoded, isMarkdown))
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
        arguments = listOf(
            navArgument("uri") { nullable = true },
            navArgument("isMarkdown") { type = NavType.BoolType; defaultValue = false }
        )
    ) { backStackEntry ->
        val uri = backStackEntry.arguments?.getString("uri")
        val isMarkdown = backStackEntry.arguments?.getBoolean("isMarkdown") ?: false
        EditorScreen(
            uriString = uri,
            isMarkdown = isMarkdown,
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
