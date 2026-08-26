package com.winlator.cmod.shared.ui.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeTheme
import kotlin.math.roundToInt

// State holder - Java-friendly mutable properties.
class PreloaderDialogState {
    val text = mutableStateOf("")
    val isIndeterminate = mutableStateOf(true)
    val progress = mutableIntStateOf(0)
    val title = mutableStateOf("")
    val badge = mutableStateOf("")
    val subtitle = mutableStateOf("")
    val stableContentLayout = mutableStateOf(false)

    /**
     * The game's own cover, shown in the middle of the splash.
     *
     * Null for everything that has no artwork to show, which is every caller
     * that existed before this: the splash then looks exactly as it did, with
     * the comet ring in the centre.
     */
    val artwork = mutableStateOf<android.graphics.Bitmap?>(null)

    /**
     * Moves the progress read-out to a bar across the bottom of the screen.
     *
     * The comet ring owns the centre, and so does the artwork, so a splash
     * showing a cover needs its progress somewhere else. Off by default, so the
     * ring stays where every other game's splash has it.
     */
    val bottomProgressBar = mutableStateOf(false)

    fun setText(value: String) {
        text.value = value
    }

    fun setIndeterminate(value: Boolean) {
        isIndeterminate.value = value
    }

    fun setProgress(value: Int) {
        progress.intValue = value
    }

    fun setTitle(value: String) {
        title.value = value
    }

    fun setBadge(value: String) {
        badge.value = value
    }

    fun setSubtitle(value: String) {
        subtitle.value = value
    }

    fun setStableContentLayout(value: Boolean) {
        stableContentLayout.value = value
    }
}

private val BgBottom = Color(0xFF000000)
private val TextPrimary = Color(0xFFF5F0EA)
private val TextSecondary = Color(0xFFC7A88F)
private val TextDim = Color(0xFF8F7862)
private val TrackColor = Color(0xFF161616)

/**
 * Height reserved at the bottom for the status line and the progress bar, so
 * the centred content never runs under them. Two lines of status, the gap, the
 * bar and its padding.
 */
private val BOTTOM_BLOCK_HEIGHT = 120.dp

private val InterFont = FontFamily(Font(R.font.inter_medium, FontWeight.Medium))
private val BricolageDisplayFont =
    FontFamily(Font(R.font.bricolage_grotesque_extrabold, FontWeight.ExtraBold))

private fun badgeStringRes(value: String): Int? =
    when (value.uppercase()) {
        "STEAM" -> R.string.preloader_platform_steam
        "EPIC" -> R.string.preloader_platform_epic
        "GOG" -> R.string.preloader_platform_gog
        "CUSTOM" -> R.string.preloader_platform_custom
        else -> null
    }

private fun badgeColor(value: String): Color =
    when (value.uppercase()) {
        "STEAM" -> Color(0xFF66C0F4)
        "EPIC" -> Color(0xFFB8BAC4)
        "GOG" -> Color(0xFFC55CFF)
        "CUSTOM" -> Color(0xFF4FE3C1)
        else -> Color(0xFFFF7A00)
    }

@Composable
fun PreloaderDialogContent(state: PreloaderDialogState) {
    val text by state.text
    val isIndeterminate by state.isIndeterminate
    val progress by state.progress
    val title by state.title
    val badge by state.badge
    val subtitle by state.subtitle
    val stableContentLayout by state.stableContentLayout
    val artwork by state.artwork
    val bottomProgressBar by state.bottomProgressBar

    val accentColor = badgeColor(badge)

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        entered = true
    }
    val contentAlpha = if (entered) 1f else 0f
    val contentRise = if (entered) 0f else 18f

    // Flat background, no gradient/glow/particle field: a loading screen just
    // needs to read clearly and get out of the way, and a static color costs
    // nothing to draw versus a per-frame animated glow + particle field.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(BgBottom),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 28.dp)
                    // The bottom bar is positioned against the screen, not
                    // placed after this column, so on a short screen -- any
                    // phone in landscape -- the centred content would otherwise
                    // run underneath it. Reserving the height it occupies keeps
                    // the two apart without either having to measure the other.
                    .padding(bottom = if (bottomProgressBar) BOTTOM_BLOCK_HEIGHT else 0.dp)
                    .offset { IntOffset(0, contentRise.roundToInt()) },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val displayTitle = title.ifEmpty { stringResource(R.string.preloader_default_name) }
            val badgeRes = badgeStringRes(badge)

            Box(
                modifier =
                    Modifier
                        .widthIn(max = 520.dp)
                        .height(if (stableContentLayout) 76.dp else 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = displayTitle,
                    fontSize = 31.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = BricolageDisplayFont,
                    color = TextPrimary.copy(alpha = contentAlpha),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (stableContentLayout || subtitle.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier =
                        Modifier
                            .widthIn(max = 440.dp)
                            .height(22.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = subtitle.ifEmpty { " " },
                        fontSize = 15.sp,
                        fontFamily = InterFont,
                        color = TextDim.copy(alpha = contentAlpha),
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (stableContentLayout || badgeRes != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier.height(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (badgeRes != null) {
                        PlatformBadge(
                            label = stringResource(badgeRes),
                            accentColor = accentColor,
                            alpha = contentAlpha,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(34.dp))

            // The centre of the splash: the game's own cover when there is one,
            // otherwise the ring this screen has always shown. Both want the
            // middle of the screen, so they are alternatives rather than a
            // stack -- and when the cover takes it, the progress moves to the
            // bar along the bottom.
            val cover = artwork
            if (cover != null) {
                Image(
                    bitmap = cover.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    alpha = contentAlpha,
                    modifier =
                        Modifier
                            .widthIn(max = 260.dp)
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(
                                width = 1.dp,
                                color = accentColor.copy(alpha = 0.28f * contentAlpha),
                                shape = RoundedCornerShape(16.dp),
                            ),
                )
            } else {
                NeonCometRing(
                    isIndeterminate = isIndeterminate,
                    progress = progress,
                    accentColor = accentColor,
                    alpha = contentAlpha,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (!bottomProgressBar) {
                run {
                    val value = text
                Text(
                    text = value,
                    fontSize = 15.sp,
                    fontFamily = InterFont,
                    color = TextSecondary.copy(alpha = contentAlpha),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
                }
            }
        }

        // Pinned to the bottom of the screen rather than placed after the
        // centred column, so it stays put as the stage text above it changes
        // length. Only drawn for a splash that moved its progress down here.
        if (bottomProgressBar) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = 28.dp)
                        .padding(bottom = 28.dp)
                        .widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = text,
                    fontSize = 15.sp,
                    fontFamily = InterFont,
                    color = TextSecondary.copy(alpha = contentAlpha),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(14.dp))
                // Smoothed, because an import reports in steps -- an unsmoothed
                // bar jumps between them, which reads as having stalled.
                val animated = (progress.coerceIn(0, 100)) / 100f
                if (isIndeterminate) {
                    LinearProgressIndicator(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        color = accentColor.copy(alpha = contentAlpha),
                        trackColor = TrackColor.copy(alpha = contentAlpha),
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                        color = accentColor.copy(alpha = contentAlpha),
                        trackColor = TrackColor.copy(alpha = contentAlpha),
                        drawStopIndicator = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformBadge(
    label: String,
    accentColor: Color,
    alpha: Float,
) {
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier =
            Modifier
                .clip(shape)
                .background(accentColor.copy(alpha = 0.1f * alpha))
                .border(width = 1.dp, color = accentColor.copy(alpha = 0.32f * alpha), shape = shape)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.9f * alpha)),
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            fontSize = 12.5.sp,
            letterSpacing = 0.3.sp,
            fontFamily = InterFont,
            fontWeight = FontWeight.Medium,
            color = accentColor.copy(alpha = alpha),
            maxLines = 1,
            // Drop default font padding and center within the line box so the label sits dead-center
            // against the dot instead of riding high.
            style =
                TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle =
                        LineHeightStyle(
                            alignment = LineHeightStyle.Alignment.Center,
                            trim = LineHeightStyle.Trim.Both,
                        ),
                ),
        )
    }
}

// Flat, single-color spinner using Compose's own CircularProgressIndicator
// instead of a hand-drawn sweep-gradient "comet" with a glowing lead dot.
// Same information (indeterminate vs. determinate progress), far less
// per-frame work: no custom trig, no gradient-stop recomputation, no extra
// glow draws - just the platform's own optimized indicator in the flat
// accent color.
@Composable
private fun NeonCometRing(
    isIndeterminate: Boolean,
    progress: Int,
    accentColor: Color,
    alpha: Float,
) {
    val animatedProgress = progress.coerceIn(0, 100) / 100f

    Box(
        modifier = Modifier.size(48.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isIndeterminate) {
            CircularProgressIndicator(
                modifier = Modifier.fillMaxSize(),
                color = accentColor.copy(alpha = alpha),
                trackColor = TrackColor.copy(alpha = 0.7f * alpha),
                strokeWidth = 4.dp,
                strokeCap = StrokeCap.Round,
            )
        } else {
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = accentColor.copy(alpha = alpha),
                trackColor = TrackColor.copy(alpha = 0.7f * alpha),
                strokeWidth = 4.dp,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}

// Java bridge - called from PreloaderDialog.java as:
// PreloaderDialogContentKt.setupPreloaderComposeView(composeView, state, activity)
fun setupPreloaderComposeView(
    composeView: ComposeView,
    state: PreloaderDialogState,
    activity: android.app.Activity,
) {
    if (activity is androidx.lifecycle.LifecycleOwner) {
        composeView.setViewTreeLifecycleOwner(activity)
    }
    if (activity is androidx.savedstate.SavedStateRegistryOwner) {
        composeView.setViewTreeSavedStateRegistryOwner(activity)
    }
    composeView.setContent {
        WinNativeTheme {
            PreloaderDialogContent(state)
        }
    }
}
