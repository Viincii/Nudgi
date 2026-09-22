package com.vincentmignot.nudgi.feature.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import com.vincentmignot.nudgi.core.accessibility.isAccessibilityServiceEnabled
import com.vincentmignot.nudgi.core.usagestats.isUsageAccessGranted
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(currentPermissionState())
        val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

        /** Re-reads permission state; call when the user returns from system settings. */
        fun refresh() {
            _uiState.value = currentPermissionState()
        }

        private fun currentPermissionState() =
            OnboardingUiState(
                hasUsageAccess = isUsageAccessGranted(context),
                hasAccessibilityAccess = isAccessibilityServiceEnabled(context),
            )
    }
