package com.winlator.cmod.shared.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object SessionDrawerStyle {
    const val SheetAlpha = 0.86f
    const val SurfaceAlpha = 0.72f
    const val PressedAlpha = 0.88f
    const val GradientLift = 0.014f

    val Accent = WinNativeAccent
    val ActiveAccent = WinNativeAccentAlt
    val FocusFill = Color(0xFF2E1A08)
    val TextPrimary = WinNativeTextPrimary.copy(alpha = 0.88f)
    val TextSecondary = WinNativeTextSecondary.copy(alpha = 0.82f)
    val Outline = WinNativeOutline
    val Background = WinNativeBackground.copy(alpha = SheetAlpha)
    val PaneSurface = WinNativeBackground.copy(alpha = SheetAlpha)
    val PaneSurfacePressed = Color(0xFF2E2117).copy(alpha = PressedAlpha)
    val TopRailSurface = WinNativeSurface.copy(alpha = SheetAlpha)
    val TileResting = Color(0xFF2A2015).copy(alpha = SurfaceAlpha)
    val TileExitResting = Color(0xFF3A2115).copy(alpha = SurfaceAlpha)
    val TileExitPressed = Color(0xFF4A2A18).copy(alpha = PressedAlpha)
    val PaneInnerResting = WinNativePanel.copy(alpha = SurfaceAlpha)
    val PaneInnerPressed = Color(0xFF2E2117).copy(alpha = PressedAlpha)
    val RestingCardBorder = WinNativeOutline.copy(alpha = 0.72f)
    val DisabledCardBorder = Color(0xFF201A14).copy(alpha = 0.58f)
    val ActiveCardBorder = ActiveAccent
    val GlassExitTint = Color(0xFFE0916B)
    val Divider = WinNativeOutline.copy(alpha = 0.6f)

    val Width = 300.dp
    val StartPadding = 6.dp
    val VerticalPadding = 6.dp
    const val PaneScaleMin = 0.78f
    const val PaneScaleReferenceHeightDp = 520f
}

object GameSettingsStyle {
    val BgDeep = Color(0xFF120E0A)
    val SidebarBg = Color(0xFF120E0A)
    val ContentBg = Color(0xFF120E0A)
    val CardSurface = WinNativeSurface
    val CardBorder = WinNativeOutline
    val InputSurface = Color(0xFF1C1611)
    val InputBorder = WinNativeOutline
    val AccentBlue = WinNativeAccent
    val TextPrimary = WinNativeTextPrimary
    val TextSecondary = WinNativeTextSecondary
    val TextDim = Color(0xFF817262)
    val Divider = WinNativeOutline
    val CheckBorder = WinNativeOutline
    val SliderInactive = WinNativeSurfaceAlt
    val ChipSurface = Color(0xFF1C1611)
    val ChipBorder = WinNativeOutline
    val DangerRed = Color(0xFFFF6B6B)
    val WarningAmber = Color(0xFFFFB74D)
    val NavHighlight = WinNativeAccentAlt
}
