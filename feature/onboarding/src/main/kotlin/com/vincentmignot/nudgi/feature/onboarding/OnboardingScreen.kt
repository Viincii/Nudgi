package com.vincentmignot.nudgi.feature.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vincentmignot.nudgi.core.designsystem.NudgiTheme

@Composable
fun OnboardingRoute(
    onOnboardingComplete: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Settings are granted outside the app, so re-check whenever the user comes back to it.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onOnboardingComplete()
    }

    val context = LocalContext.current
    // Once denied twice, Android stops showing the dialog and the request fails at once, so a
    // refusal falls back to the app's notification settings.
    val notificationPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) viewModel.refresh() else openNotificationSettings(context)
        }

    OnboardingScreen(
        uiState = uiState,
        onEnableNotifications = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                openNotificationSettings(context)
            }
        },
        modifier = modifier,
    )
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}

@Composable
internal fun OnboardingScreen(
    uiState: OnboardingUiState,
    onEnableNotifications: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(text = stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineMedium)
            Text(text = stringResource(R.string.onboarding_subtitle), style = MaterialTheme.typography.bodyLarge)
            PermissionCard(
                title = stringResource(R.string.onboarding_usage_access_title),
                description = stringResource(R.string.onboarding_usage_access_description),
                granted = uiState.hasUsageAccess,
                onOpenSettings = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            )
            PermissionCard(
                title = stringResource(R.string.onboarding_accessibility_title),
                description = stringResource(R.string.onboarding_accessibility_description),
                granted = uiState.hasAccessibilityAccess,
                onOpenSettings = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
            PermissionCard(
                title = stringResource(R.string.onboarding_notifications_title),
                description = stringResource(R.string.onboarding_notifications_description),
                granted = uiState.hasNotifications,
                actionLabel = stringResource(R.string.onboarding_allow),
                onOpenSettings = onEnableNotifications,
            )
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    granted: Boolean,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String = stringResource(R.string.onboarding_open_settings),
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
            if (granted) {
                Text(
                    text = stringResource(R.string.onboarding_permission_granted),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Button(onClick = onOpenSettings) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    NudgiTheme {
        OnboardingScreen(uiState = OnboardingUiState(), onEnableNotifications = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingScreenPartiallyGrantedPreview() {
    NudgiTheme {
        OnboardingScreen(uiState = OnboardingUiState(hasUsageAccess = true), onEnableNotifications = {})
    }
}
