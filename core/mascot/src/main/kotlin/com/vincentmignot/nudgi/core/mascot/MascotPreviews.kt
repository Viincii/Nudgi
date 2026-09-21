package com.vincentmignot.nudgi.core.mascot

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Preview(showBackground = true)
@Composable
private fun NudgiMascotMoodsPreview() {
    Row {
        MascotMood.entries.forEach { mood ->
            NudgiMascot(mood = mood, modifier = Modifier.size(120.dp))
        }
    }
}
