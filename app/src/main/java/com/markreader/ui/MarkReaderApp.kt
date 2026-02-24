package com.markreader.ui

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.markreader.ui.navigation.NavRoutes
import com.markreader.ui.navigation.markReaderNavGraph

@Composable
fun MarkReaderApp(
    externalUri: String?,
    launchedExternally: Boolean
) {
    val navController = rememberNavController()
    val startDestination = rememberSaveable(launchedExternally, externalUri) {
        if (launchedExternally && !externalUri.isNullOrBlank()) {
            val encoded = Uri.encode(externalUri)
            NavRoutes.Viewer.createRoute(encoded)
        } else {
            NavRoutes.Home.route
        }
    }
    var lastHandledExternalUri by rememberSaveable {
        mutableStateOf(if (launchedExternally && !externalUri.isNullOrBlank()) externalUri else null)
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(
                    initialScale = 0.98f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
        },
        exitTransition = {
            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleOut(
                    targetScale = 1.02f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
        },
        popEnterTransition = {
            fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleIn(
                    initialScale = 0.98f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
        },
        popExitTransition = {
            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                scaleOut(
                    targetScale = 1.02f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                )
        }
    ) {
        markReaderNavGraph(navController, externalUri)
    }

    LaunchedEffect(externalUri) {
        if (!externalUri.isNullOrBlank() && externalUri != lastHandledExternalUri) {
            navigateToViewerClearingBackStack(navController, externalUri)
            lastHandledExternalUri = externalUri
        }
    }
}

private fun navigateToViewerClearingBackStack(
    navController: NavHostController,
    uriString: String
) {
    val encoded = Uri.encode(uriString)
    navController.navigate(NavRoutes.Viewer.createRoute(encoded)) {
        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}
