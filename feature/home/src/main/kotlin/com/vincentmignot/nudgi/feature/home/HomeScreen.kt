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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.vincentmignot.nudgi.core.nudge.NudgeRule
import com.vincentmignot.nudgi.core.today.TodayNudge
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private const val MINUTE_MS = 60_000L

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
    onMoodSelected: (MascotMood?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NudgiMascot(mood = uiState.mood, modifier = Modifier.widthIn(max = 240.dp).fillMaxWidth())
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(homeContent(uiState.mood).messageRes),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (uiState.isLoaded) {
                Text(
                    text = usageText(uiState.watchedUsageMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                TodayNudges(nudges = uiState.nudges, modifier = Modifier.fillMaxWidth())
            }
            if (showDebugControls) {
                Spacer(Modifier.height(16.dp))
                MoodPicker(
                    selected = uiState.mood.takeIf { uiState.isMoodOverridden },
                    onMoodSelected = onMoodSelected,
                )
            }
        }
    }
}

@Composable
private fun TodayNudges(
    nudges: List<TodayNudge>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stringResource(R.string.home_nudges_title), style = MaterialTheme.typography.titleMedium)
        if (nudges.isEmpty()) {
            Text(
                text = stringResource(R.string.home_nudges_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val timeFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                nudges.forEachIndexed { index, nudge ->
                    if (index > 0) HorizontalDivider()
                    ListItem(
                        headlineContent = { Text(nudge.appLabel) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.home_nudge_supporting,
                                    nudge.time.format(timeFormatter),
                                    stringResource(ruleLabelRes(nudge.rule)),
                                ),
                            )
                        },
                        trailingContent = {
                            outcomeLabelRes(nudge.outcome)?.let { Text(stringResource(it)) }
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                }
            }
        }
    }
}

/** Lets a debug build force a mood; "Live" goes back to the one derived from today. */
@Composable
private fun MoodPicker(
    selected: MascotMood?,
    onMoodSelected: (MascotMood?) -> Unit,
) {
    val options = listOf(null) + MascotMood.entries
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = stringResource(R.string.home_debug_mood_picker), style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, mood ->
                SegmentedButton(
                    selected = mood == selected,
                    onClick = { onMoodSelected(mood) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(stringResource(mood?.let { homeContent(it).labelRes } ?: R.string.home_mood_live))
                }
            }
        }
    }
}

@Composable
private fun usageText(usageMs: Long): String {
    val minutes = (usageMs / MINUTE_MS).toInt()
    return if (minutes < 60) {
        stringResource(R.string.home_usage_minutes, minutes)
    } else {
        stringResource(R.string.home_usage_hours, minutes / 60, minutes % 60)
    }
}

@StringRes
private fun ruleLabelRes(rule: NudgeRule): Int =
    when (rule) {
        NudgeRule.LateNight -> R.string.home_rule_late_night
        NudgeRule.SnoozeFollowUp -> R.string.home_rule_snooze_followup
        NudgeRule.LongSession -> R.string.home_rule_long_session
        NudgeRule.DailyBudget -> R.string.home_rule_daily_budget
    }

@StringRes
private fun outcomeLabelRes(outcome: TodayNudge.Outcome): Int? =
    when (outcome) {
        TodayNudge.Outcome.TookABreak -> R.string.home_outcome_took_a_break
        TodayNudge.Outcome.KeptGoing -> R.string.home_outcome_kept_going
        TodayNudge.Outcome.Pending -> null
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
        HomeScreen(
            uiState =
                HomeUiState(
                    mood = MascotMood.Neutral,
                    isLoaded = true,
                    watchedUsageMs = 72 * MINUTE_MS,
                    nudges =
                        listOf(
                            TodayNudge(
                                "n1",
                                LocalTime.of(9, 42),
                                "Instagram",
                                NudgeRule.LongSession,
                                TodayNudge.Outcome.TookABreak,
                            ),
                            TodayNudge(
                                "n2",
                                LocalTime.of(13, 5),
                                "YouTube",
                                NudgeRule.DailyBudget,
                                TodayNudge.Outcome.KeptGoing,
                            ),
                            TodayNudge(
                                "n3",
                                LocalTime.of(18, 30),
                                "Reddit",
                                NudgeRule.LongSession,
                                TodayNudge.Outcome.Pending,
                            ),
                        ),
                ),
            showDebugControls = true,
            onMoodSelected = {},
        )
    }
}
