package com.winlator.cmod.shared.theme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val WinNativeBackground = Color(0xFF18181D)
val WinNativeSurface = Color(0xFF1C1C2A)
val WinNativeSurfaceAlt = Color(0xFF21212A)
val WinNativePanel = Color(0xFF161622)
val WinNativeOutline = Color(0xFF2A2A3A)
val WinNativeAccent = Color(0xFF1A9FFF)
val WinNativeTextPrimary = Color(0xFFF0F4FF)
val WinNativeTextSecondary = Color(0xFF7A8FA8)
val WinNativeDanger = Color(0xFFFF7A88)

private val WinNativeColorScheme =
    darkColorScheme(
        primary = WinNativeAccent,
        background = WinNativeBackground,
        surface = WinNativeSurface,
        onSurface = WinNativeTextPrimary,
        onBackground = WinNativeTextPrimary,
    )

@Composable
fun WinNativeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WinNativeColorScheme,
        content = content,
    )
}
