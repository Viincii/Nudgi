package com.vincentmignot.nudgi.feature.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vincentmignot.nudgi.core.designsystem.NudgiTheme
import com.vincentmignot.nudgi.core.mascot.MascotMood
import com.vincentmignot.nudgi.core.mascot.NudgiMascot

@Composable
fun HomeRoute(
    showDebugControls: Boolean,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        uiState = uiState,
        showDebugControls = showDebugControls,
        onMoodSelected = viewModel::onMoodSelected,
        modifier = modifier,
    )
}

@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    showDebugControls: Boolean,
    onMoodSelected: (MascotMood) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NudgiMascot(mood = uiState.mood, modifier = Modifier.widthIn(max = 280.dp).fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(homeContent(uiState.mood).messageRes),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
            if (showDebugControls) {
                MoodPicker(selected = uiState.mood, onMoodSelected = onMoodSelected)
            }
        }
    }
}

/** Lets a debug build cycle through moods until real signals drive them. */
@Composable
private fun MoodPicker(
    selected: MascotMood,
    onMoodSelected: (MascotMood) -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = stringResource(R.string.home_debug_mood_picker), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            MascotMood.entries.forEachIndexed { index, mood ->
                SegmentedButton(
                    selected = mood == selected,
                    onClick = { onMoodSelected(mood) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = MascotMood.entries.size),
                ) {
                    Text(stringResource(homeContent(mood).labelRes))
                }
            }
        }
    }
}

private data class HomeMoodContent(
    @StringRes val messageRes: Int,
    @StringRes val labelRes: Int,
)

private fun homeContent(mood: MascotMood): HomeMoodContent =
    when (mood) {
        MascotMood.Happy -> HomeMoodContent(R.string.home_message_happy, R.string.home_mood_happy)
        MascotMood.Neutral -> HomeMoodContent(R.string.home_message_neutral, R.string.home_mood_neutral)
        MascotMood.Worried -> HomeMoodContent(R.string.home_message_worried, R.string.home_mood_worried)
    }

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    NudgiTheme {
        HomeScreen(uiState = HomeUiState(), showDebugControls = true, onMoodSelected = {})
    }
}
