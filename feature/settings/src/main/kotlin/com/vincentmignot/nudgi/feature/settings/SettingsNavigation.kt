package com.vincentmignot.nudgi.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

const val SETTINGS_ROUTE = "settings"

fun NavGraphBuilder.settingsScreen() {
    composable(SETTINGS_ROUTE) {
        SettingsRoute()
    }
}
