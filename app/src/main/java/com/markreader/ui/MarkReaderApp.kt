package com.markreader.ui

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
    externalUriNonce: Long = 0L,
    launchedExternally: Boolean
) {
    val navController = rememberNavController()
    val startDestination = rememberSaveable {
        if (launchedExternally && !externalUri.isNullOrBlank()) {
            NavRoutes.Viewer.createRoute(externalUri)
        } else {
            NavRoutes.Home.route
        }
    }
    var lastHandledNonce by rememberSaveable {
        mutableStateOf(if (launchedExternally && !externalUri.isNullOrBlank()) externalUriNonce else -1L)
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

    LaunchedEffect(externalUriNonce) {
        if (!externalUri.isNullOrBlank() && externalUriNonce != lastHandledNonce) {
            navigateToViewerClearingBackStack(navController, externalUri)
            lastHandledNonce = externalUriNonce
        }
    }
}

private fun navigateToViewerClearingBackStack(
    navController: NavHostController,
    uriString: String
) {
    navController.navigate(NavRoutes.Viewer.createRoute(uriString)) {
        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
        launchSingleTop = true
    }
}
