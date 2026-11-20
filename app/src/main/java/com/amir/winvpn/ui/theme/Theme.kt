package com.amir.winvpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object WinColors {
    val Background = Color(0xFF060B18)
    val Surface = Color(0xFF0E1A33)
    val SurfaceHigh = Color(0xFF15264A)
    val Accent = Color(0xFF2F80FF)
    val Connected = Color(0xFF2ECC71)
    val Connecting = Color(0xFFFFB020)
    val Disconnected = Color(0xFF6B7A99)
    val TextPrimary = Color(0xFFEAF0FF)
    val TextSecondary = Color(0xFF8FA0C2)
    val Error = Color(0xFFFF5C6C)
}

private val Scheme = darkColorScheme(
    primary = WinColors.Accent,
    onPrimary = Color.White,
    background = WinColors.Background,
    onBackground = WinColors.TextPrimary,
    surface = WinColors.Surface,
    onSurface = WinColors.TextPrimary,
    surfaceVariant = WinColors.SurfaceHigh,
    onSurfaceVariant = WinColors.TextSecondary,
    error = WinColors.Error,
    outline = WinColors.Disconnected
)

@Composable
fun WinVpnTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
