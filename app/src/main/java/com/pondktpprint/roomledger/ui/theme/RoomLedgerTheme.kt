package com.pondktpprint.roomledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF216B52),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC7F0D9),
    onPrimaryContainer = Color(0xFF002114),
    secondary = Color(0xFF4D6358),
    background = Color(0xFFF7F8F5),
    surface = Color(0xFFF7F8F5),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD4B8),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF075138),
    onPrimaryContainer = Color(0xFFB6F1D2),
    secondary = Color(0xFFB5CCBE),
    background = Color(0xFF111411),
    surface = Color(0xFF111411),
)

@Composable
fun RoomLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
