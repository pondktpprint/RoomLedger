package com.pondktpprint.roomledger.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Color(0xFF087653),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F5E7),
    onPrimaryContainer = Color(0xFF07563F),
    secondary = Color(0xFF07563F),
    onSecondary = Color.White,
    tertiary = Color(0xFF16865F),
    background = Color(0xFFFAFAF7),
    surface = Color(0xFFFAFAF7),
    surfaceVariant = Color(0xFFF0F2EE),
    onSurface = Color(0xFF202A27),
    onSurfaceVariant = Color(0xFF737D79),
    error = Color(0xFFD64545),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6BD0A5),
    onPrimary = Color(0xFF003D2B),
    primaryContainer = Color(0xFF07563F),
    onPrimaryContainer = Color(0xFFD9F5E7),
    secondary = Color(0xFF9FCDB6),
    onSecondary = Color(0xFF073A2C),
    tertiary = Color(0xFF9CE7C3),
    background = Color(0xFF111A16),
    surface = Color(0xFF111A16),
    surfaceVariant = Color(0xFF27332D),
    onSurface = Color(0xFFE3EAE5),
    onSurfaceVariant = Color(0xFFBAC6BF),
    error = Color(0xFFFFB4AB),
)

@Composable
fun RoomLedgerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
        content = content,
    )
}
