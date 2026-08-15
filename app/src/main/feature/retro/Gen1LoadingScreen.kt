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

@Composable
fun Gen1LoadingScreen(
    gameName: String,
    artwork: android.graphics.Bitmap?,
    state: Gen1EngineBridge.Import?,
    visible: Boolean,
) {
    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.EnterTransition.None,
        exit = fadeOut(tween(320)),
    ) {
        val context = LocalContext.current
        val preloader = remember { PreloaderDialogState() }

        preloader.title.value = gameName.ifBlank { stringResource(R.string.preloader_default_name) }
        preloader.subtitle.value = stringResource(R.string.retro_engine_loading_subtitle)
        preloader.artwork.value = artwork
        preloader.bottomProgressBar.value = true
        preloader.text.value =
            state?.stage?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.retro_engine_loading)
        preloader.isIndeterminate.value = state == null
        preloader.progress.intValue = ((state?.progress ?: 0f) * 100f).toInt().coerceIn(0, 100)

        PreloaderDialogContent(preloader)
    }
}
