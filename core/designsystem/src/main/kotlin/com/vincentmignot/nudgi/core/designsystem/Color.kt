package com.vincentmignot.nudgi.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

internal val LightColors =
    lightColorScheme(
        primary = Color(0xFF2F8F7F),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFC8F2E8),
        onPrimaryContainer = Color(0xFF0B2B26),
        background = Color(0xFFF6FBF9),
        onBackground = Color(0xFF15211F),
        surface = Color(0xFFF6FBF9),
        onSurface = Color(0xFF15211F),
    )

internal val DarkColors =
    darkColorScheme(
        primary = Color(0xFF7BD8C4),
        onPrimary = Color(0xFF00382F),
        primaryContainer = Color(0xFF1F5A4F),
        onPrimaryContainer = Color(0xFFC8F2E8),
        background = Color(0xFF0E1513),
        onBackground = Color(0xFFDCE8E5),
        surface = Color(0xFF0E1513),
        onSurface = Color(0xFFDCE8E5),
    )
