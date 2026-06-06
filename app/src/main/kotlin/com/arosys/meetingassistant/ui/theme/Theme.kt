package com.arosys.meetingassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4CAF50),          // green — recording active
    onPrimary = Color.White,
    secondary = Color(0xFF03A9F4),        // blue — model/status info
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    background = Color(0xFF1A1A1A),
    error = Color(0xFFCF6679),
)

@Composable
fun MeetingAssistantTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}

/** Accent used when recording is active. */
val RecordingRed = Color(0xFFE53935)
val PartialTextColor = Color(0xFF9E9E9E)
