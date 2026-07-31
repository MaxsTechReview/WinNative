package com.winlator.cmod.feature.retro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winlator.cmod.R

/**
 * Covers the screen while a game is imported for the first time.
 *
 * The engine draws its own importer screen -- its logo, its progress bars --
 * and on this path the player never asked for that: they picked a game in
 * WinNative and pressed Play. So the engine's screen is covered with the game's
 * own artwork and a progress bar fed from the import itself, and it fades out
 * once the game has booted.
 *
 * Only ever seen once per game. After the import the engine boots straight into
 * the game and this never appears again, which is why it is deliberately plain:
 * anything more elaborate would be work the player sees once.
 */
@Composable
fun Gen1LoadingScreen(
    gameName: String,
    artwork: android.graphics.Bitmap?,
    state: Gen1EngineBridge.Import?,
    visible: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        // No enter transition: this is already on screen when the activity
        // opens. Fading in would show the engine's splash underneath it first,
        // which is the thing it exists to hide.
        enter = androidx.compose.animation.EnterTransition.None,
        exit = fadeOut(tween(320)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF07090D)),
            contentAlignment = Alignment.Center,
        ) {
            // Blurring is not available this far back, so the backdrop is the
            // artwork faded almost out -- enough to colour the screen with the
            // game without competing with the card in front of it.
            artwork?.let { art ->
                Image(
                    bitmap = art.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.18f,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xCC07090D), Color(0xF207090D)),
                            ),
                        ),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.padding(32.dp).widthIn(max = 360.dp),
            ) {
                artwork?.let { art ->
                    Image(
                        bitmap = art.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .widthIn(max = 200.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }

                Text(
                    text = gameName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )

                // The engine reports a real fraction, so this is a real bar.
                // It is animated because the import moves in steps -- an
                // unsmoothed bar jumps, which reads as though it has stalled.
                val progress by animateFloatAsState(
                    targetValue = state?.progress ?: 0f,
                    animationSpec = tween(240),
                    label = "importProgress",
                )
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF1A9FFF),
                    trackColor = Color(0x33FFFFFF),
                )

                Text(
                    // The engine names the stage it is on ("Verifying ...",
                    // "Preparing private game data"), which is more use than a
                    // percentage on its own. Before the first report there is
                    // nothing truthful to say beyond that it is loading.
                    text = state?.stage?.takeIf { it.isNotBlank() }
                        ?: stringResourceSafe(R.string.retro_engine_loading),
                    color = Color(0xB3FFFFFF),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * The loading screen is hosted outside a normal Compose activity, so a missing
 * resource should not take the game down with it.
 */
@Composable
private fun stringResourceSafe(id: Int): String =
    runCatching { androidx.compose.ui.res.stringResource(id) }.getOrDefault("Loading")
