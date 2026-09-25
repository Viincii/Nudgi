package com.vincentmignot.nudgi.feature.home

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val HOME_ROUTE = "home"

fun NavGraphBuilder.homeScreen(
    showDebugControls: Boolean,
    onOpenSettings: () -> Unit,
) {
    composable(HOME_ROUTE) {
        HomeRoute(showDebugControls = showDebugControls, onOpenSettings = onOpenSettings)
    }
}
