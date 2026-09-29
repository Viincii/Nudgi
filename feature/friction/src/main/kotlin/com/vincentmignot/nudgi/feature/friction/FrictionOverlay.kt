package com.vincentmignot.nudgi.feature.friction

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vincentmignot.nudgi.core.designsystem.NudgiTheme
import com.vincentmignot.nudgi.core.mascot.MascotMood
import com.vincentmignot.nudgi.core.mascot.NudgiMascot
import kotlinx.coroutines.delay
import com.vincentmignot.nudgi.core.nudge.R as NudgeR

private const val SECOND_MS = 1_000L

/**
 * Drawn over a watched app: Nudgi, with the expression the coach chose, and the nudge's message. "I'll stop" is the main
 * action. "5 more minutes" stays locked for [countdownMs], shown as a countdown, when there is one.
 */
@Composable
fun FrictionOverlay(
    title: String,
    message: String,
    mood: MascotMood,
    countdownMs: Long,
    onStop: () -> Unit,
    onSnooze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var remainingS by remember { mutableIntStateOf(((countdownMs + SECOND_MS - 1) / SECOND_MS).toInt()) }
    LaunchedEffect(Unit) {
        while (remainingS > 0) {
            delay(SECOND_MS)
            remainingS--
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Card(modifier = Modifier.padding(24.dp).widthIn(max = 420.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                NudgiMascot(mood = mood, modifier = Modifier.widthIn(max = 160.dp).fillMaxWidth())
                Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(text = message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(NudgeR.string.nudge_action_stop))
                }
                OutlinedButton(onClick = onSnooze, enabled = remainingS == 0, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (remainingS > 0) {
                            stringResource(R.string.friction_snooze_countdown, remainingS)
                        } else {
                            stringResource(NudgeR.string.nudge_action_snooze)
                        },
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun FrictionOverlayPreview() {
    NudgiTheme {
        FrictionOverlay(
            title = "Hey, it's Nudgi",
            message = "Your five more minutes on Instagram are up.",
            mood = MascotMood.Worried,
            countdownMs = 10_000L,
            onStop = {},
            onSnooze = {},
        )
    }
}
