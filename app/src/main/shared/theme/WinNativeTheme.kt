package com.winlator.cmod.shared.theme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.winlator.cmod.R

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

val WinNativeFontFamily =
    FontFamily(
        Font(R.font.inter_medium, FontWeight.Normal),
        Font(R.font.inter_medium, FontWeight.Medium),
        Font(R.font.inter_medium, FontWeight.SemiBold),
        Font(R.font.inter_medium, FontWeight.Bold),
    )

private val BaseTypography = Typography()

val WinNativeTypography =
    Typography(
        displayLarge = BaseTypography.displayLarge.copy(fontFamily = WinNativeFontFamily),
        displayMedium = BaseTypography.displayMedium.copy(fontFamily = WinNativeFontFamily),
        displaySmall = BaseTypography.displaySmall.copy(fontFamily = WinNativeFontFamily),
        headlineLarge = BaseTypography.headlineLarge.copy(fontFamily = WinNativeFontFamily),
        headlineMedium = BaseTypography.headlineMedium.copy(fontFamily = WinNativeFontFamily),
        headlineSmall = BaseTypography.headlineSmall.copy(fontFamily = WinNativeFontFamily),
        titleLarge = BaseTypography.titleLarge.copy(fontFamily = WinNativeFontFamily),
        titleMedium = BaseTypography.titleMedium.copy(fontFamily = WinNativeFontFamily),
        titleSmall = BaseTypography.titleSmall.copy(fontFamily = WinNativeFontFamily),
        bodyLarge = BaseTypography.bodyLarge.copy(fontFamily = WinNativeFontFamily),
        bodyMedium = BaseTypography.bodyMedium.copy(fontFamily = WinNativeFontFamily),
        bodySmall = BaseTypography.bodySmall.copy(fontFamily = WinNativeFontFamily),
        labelLarge = BaseTypography.labelLarge.copy(fontFamily = WinNativeFontFamily),
        labelMedium = BaseTypography.labelMedium.copy(fontFamily = WinNativeFontFamily),
        labelSmall = BaseTypography.labelSmall.copy(fontFamily = WinNativeFontFamily),
    )

@Composable
fun WinNativeTheme(
    colorScheme: ColorScheme = WinNativeColorScheme,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = WinNativeTypography,
        content = content,
    )
}
