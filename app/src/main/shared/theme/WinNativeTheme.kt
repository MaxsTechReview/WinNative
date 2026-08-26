package com.winlator.cmod.shared.theme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.winlator.cmod.R

// Orange-black palette: this file is the single source of truth for the app's
// Compose color tokens. True/solid black surfaces (no warm brown tint) with one
// saturated orange accent; corner radius + border width carry depth instead of
// elevation/shadow, so the surface steps below only need to be a few percent
// apart to read as "layered" against a solid black background.
val WinNativeBackground = Color(0xFF000000)
val WinNativeSurface = Color(0xFF0D0D0D)
val WinNativeSurfaceAlt = Color(0xFF161616)
val WinNativePanel = Color(0xFF000000)
val WinNativeOutline = Color(0xFF262626)
val WinNativeAccent = Color(0xFFFF7A00) // primary orange
val WinNativeAccentAlt = Color(0xFFFFA940) // secondary accent for status/links
val WinNativeTextPrimary = Color(0xFFF5F0EA)
val WinNativeTextSecondary = Color(0xFFAD9782)
val WinNativeDanger = Color(0xFFFF7A88)

// Flat design tokens: no elevation/shadow, corner radius and border width
// are the only depth cues. Cheaper to draw than shadow() (no extra
// rasterization/blur pass) and matches the flat Switch home-menu look.
val WinNativeCardShape = RoundedCornerShape(16.dp)
val WinNativeChipShape = RoundedCornerShape(12.dp)
val WinNativeBorderWidth = 1.dp

private val WinNativeShapes =
    Shapes(
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(20.dp),
    )

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
        shapes = WinNativeShapes,
        content = content,
    )
}
