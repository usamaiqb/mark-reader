package com.markreader.ui.navigation

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.navArgument
import androidx.navigation.compose.composable
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
            }
        )
    }

    composable(
        route = NavRoutes.Viewer.route,
        arguments = listOf(navArgument("uri") { nullable = true })
    ) { backStackEntry ->
        val uri = backStackEntry.arguments?.getString("uri")?.let(Uri::decode) ?: externalUri
        ViewerScreen(
            onOpenSettings = { navController.navigateToSettings() },
            uriString = uri
        )
    }

    composable(NavRoutes.Settings.route) {
        SettingsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
}

private fun NavController.navigateToSettings() {
    navigate(NavRoutes.Settings.route) {
        launchSingleTop = true
    }
}
