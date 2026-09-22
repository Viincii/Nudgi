package com.vincentmignot.nudgi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.vincentmignot.nudgi.core.designsystem.NudgiTheme
import com.vincentmignot.nudgi.feature.home.HOME_ROUTE
import com.vincentmignot.nudgi.feature.home.homeScreen
import com.vincentmignot.nudgi.feature.onboarding.ONBOARDING_ROUTE
import com.vincentmignot.nudgi.feature.onboarding.hasCompletedOnboarding
import com.vincentmignot.nudgi.feature.onboarding.onboardingScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NudgiTheme {
                val context = LocalContext.current
                val navController = rememberNavController()
                val startDestination =
                    remember { if (hasCompletedOnboarding(context)) HOME_ROUTE else ONBOARDING_ROUTE }

                NavHost(navController = navController, startDestination = startDestination) {
                    onboardingScreen(
                        onOnboardingComplete = {
                            navController.navigate(HOME_ROUTE) {
                                popUpTo(ONBOARDING_ROUTE) { inclusive = true }
                            }
                        },
                    )
                    homeScreen(showDebugControls = BuildConfig.DEBUG)
                }
            }
        }
    }
}
