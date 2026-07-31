package com.winlator.cmod.feature.retro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.winlator.cmod.R
import com.winlator.cmod.shared.ui.dialog.PreloaderDialogContent
import com.winlator.cmod.shared.ui.dialog.PreloaderDialogState

/**
 * Covers the screen while a game is imported for the first time.
 *
 * The engine draws its own importer screen -- its logo, its progress bars --
 * and on this path the player never asked for that: they picked a game in
 * WinNative and pressed Play, so what they should see is WinNative starting a
 * game, exactly as it looks for every other game in the library.
 *
 * So this is the app's own preloader rather than anything built for the engine.
 * The only two differences are the ones this screen has that a PC game's does
 * not: the shortcut's cover art in the middle, and a real progress bar along
 * the bottom, because the engine reports how far the import has actually got.
 *
 * Only ever seen once per game. After the import the engine boots straight into
 * the game and this never appears again.
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
        val context = LocalContext.current
        val preloader = remember { PreloaderDialogState() }

        preloader.title.value = gameName.ifBlank { stringResource(R.string.preloader_default_name) }
        preloader.subtitle.value = stringResource(R.string.retro_engine_loading_subtitle)
        preloader.artwork.value = artwork
        preloader.bottomProgressBar.value = true
        // The engine names the stage it is on ("Verifying ...", "Preparing
        // private game data"), which is more use than a percentage on its own.
        // Before the first report there is nothing truthful to say beyond that
        // it is loading.
        preloader.text.value =
            state?.stage?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.retro_engine_loading)
        // Indeterminate until the engine reports a figure, so the bar never
        // claims a progress it does not have.
        preloader.isIndeterminate.value = state == null
        preloader.progress.intValue = ((state?.progress ?: 0f) * 100f).toInt().coerceIn(0, 100)

        PreloaderDialogContent(preloader)
    }
}
