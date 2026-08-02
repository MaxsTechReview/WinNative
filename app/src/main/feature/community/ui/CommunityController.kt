package com.winlator.cmod.feature.community.ui

import android.app.Activity
import android.app.Dialog
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import com.winlator.cmod.R
import com.winlator.cmod.feature.community.ComponentChecker
import com.winlator.cmod.feature.community.ConfigApplier
import com.winlator.cmod.feature.community.ConfigSerializer
import com.winlator.cmod.feature.community.DeviceIdentity
import com.winlator.cmod.feature.community.UploaderIdentity
import com.winlator.cmod.feature.community.net.CommunityApiClient
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.runtime.content.ContentsManager
import com.winlator.cmod.shared.theme.WinNativeTheme
import com.winlator.cmod.shared.ui.nav.PaneNavRegistry
import com.winlator.cmod.shared.ui.nav.bindPaneNav
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class CommunityController(
    private val activity: Activity,
    private val shortcut: Shortcut,
    private val contentsManager: ContentsManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val api = CommunityApiClient(activity)
    private var downloadDialog: Dialog? = null
    private var restoreDownloadNav: (() -> Unit)? = null
    private var overlayBack: (() -> Boolean)? = null

    var onConfigApplied: () -> Unit = {}

    private fun guard(what: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.e(TAG, "community $what failed", t)
            toast("Community $what failed: ${t.javaClass.simpleName}: ${t.message ?: "no message"}")
        }
    }

    init {
        UploaderIdentity.resolveGoogle(activity)
    }

    fun upload() = guard("upload") { uploadInternal() }

    private fun uploadInternal() {
        if (!CommunityApiClient.isConfigured()) {
            toast("Community sharing is not available in this build")
            return
        }
        ensureGoogle {
            toast("Uploading…")
            scope.launch {
                runCatching {
                    val settings = ConfigSerializer.serialize(shortcut)
                    val skipped = ConfigSerializer.rejectedKeys(shortcut)
                    val gameKey = ConfigSerializer.gameKey(shortcut)
                    val store = ConfigSerializer.storeOf(shortcut)
                    val hw = DeviceIdentity.current()
                    api.upload(gameKey, store, settings, hw) to skipped
                }.onSuccess { (result, skipped) ->
                    val dropped = (result.droppedKeys + skipped).distinct()
                    if (dropped.isEmpty()) {
                        toast("Upload successful")
                    } else {
                        toast("Upload successful — skipped: ${dropped.joinToString(", ")}")
                    }
                }.onFailure {
                    toast("Upload failed: ${it.message ?: "unknown error"}")
                }
            }
        }
    }

    fun ensureGoogle(action: () -> Unit) {
        if (UploaderIdentity.isGoogleBacked()) {
            action()
            return
        }
        toast("Sign in with a Google account…")
        UploaderIdentity.signInAndResolve(activity) { ok ->
            if (ok) action() else toast("Google sign-in is required to upload or vote")
        }
    }

    fun dispose() {
        restoreDownloadNav?.invoke()
        restoreDownloadNav = null
        overlayBack = null
        downloadDialog = null
        scope.cancel()
    }

    private fun toast(msg: String) {
        activity.runOnUiThread { Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() }
    }

    fun openDownload() = guard("download") { openDownloadInternal() }

    private fun openDownloadInternal() {
        val lifecycleOwner = activity as? LifecycleOwner
        val savedStateOwner = activity as? SavedStateRegistryOwner
        if (lifecycleOwner == null || savedStateOwner == null) {
            toast("Community sharing is unavailable here")
            return
        }
        if (!CommunityApiClient.isConfigured()) {
            toast("Community sharing is not available in this build")
            return
        }
        val gameKey = ConfigSerializer.gameKey(shortcut)
        val hw = DeviceIdentity.current()
        val navRegistry = PaneNavRegistry()
        val dialog = Dialog(activity, R.style.ContentDialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setCanceledOnTouchOutside(false)
            setOwnerActivity(activity)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setGravity(Gravity.CENTER)
                setDimAmount(0.5f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
            }
            setOnDismissListener {
                restoreDownloadNav?.invoke()
                restoreDownloadNav = null
                overlayBack = null
                downloadDialog = null
            }
        }
        downloadDialog = dialog
        val composeView = ComposeView(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(savedStateOwner)
            setContent {
                WinNativeTheme {
                    CommunityConfigDownloadScreen(
                        gameTitle = shortcut.name,
                        gameKey = gameKey,
                        hw = hw,
                        api = api,
                        registry = navRegistry,
                        applyConfig = { settings -> applyConfig(settings) },
                        onAppliedDismiss = {
                            onConfigApplied()
                            toast("Config applied")
                            dialog.dismiss()
                        },
                        onClose = { dialog.dismiss() },
                        toast = { msg -> toast(msg) },
                        voteGate = { act -> ensureGoogle(act) },
                        openPreview = { settings -> openPreview(settings) },
                        registerBackHandler = { handler -> overlayBack = handler },
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.show()
        restoreDownloadNav = dialog.window?.bindPaneNav(
            navRegistry,
            onDismiss = { if (overlayBack?.invoke() != true) dialog.dismiss() },
        )
        sizeToHost(dialog)
        dialog.window?.decorView?.post { sizeToHost(dialog) }
    }

    private fun sizeToHost(dialog: Dialog) {
        val metrics = activity.resources.displayMetrics
        val host = activity.window.decorView
        val w = if (host.width > 0) host.width else metrics.widthPixels
        val h = if (host.height > 0) host.height else metrics.heightPixels

        var horizontalInset = 0
        var verticalInset = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = activity.windowManager.currentWindowMetrics.windowInsets
            val bars = insets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars())
            val cutout = insets.getInsetsIgnoringVisibility(WindowInsets.Type.displayCutout())
            val cap = (CUTOUT_PADDING_DP * metrics.density).toInt()
            horizontalInset = maxOf(
                maxOf(bars.left, cutout.left.coerceAtMost(cap)),
                maxOf(bars.right, cutout.right.coerceAtMost(cap)),
            )
            verticalInset = maxOf(
                maxOf(bars.top, cutout.top.coerceAtMost(cap)),
                maxOf(bars.bottom, cutout.bottom.coerceAtMost(cap)),
            )
        }
        val edge = (EDGE_PADDING_DP * metrics.density).toInt().coerceAtLeast(1)
        val maxWidth = (w - (horizontalInset + edge) * 2).coerceAtLeast(1)
        val maxHeight = (h - (verticalInset + edge) * 2).coerceAtLeast(1)

        dialog.window?.setLayout(
            (w * 0.96f).toInt().coerceAtMost(maxWidth),
            (h * 0.92f).toInt().coerceAtMost(maxHeight),
        )
        dialog.window?.setGravity(Gravity.CENTER)
    }

    private fun openPreview(settings: JSONObject) {
        com.winlator.cmod.feature.shortcuts.ShortcutSettingsComposeDialog.preview(
            activity, shortcut, settings,
        ) {
            onConfigApplied()
            downloadDialog?.dismiss()
        }
    }

    private companion object {
        const val TAG = "CommunityController"
        const val EDGE_PADDING_DP = 12f
        const val CUTOUT_PADDING_DP = 8f
    }

    private suspend fun applyConfig(settings: JSONObject): List<ComponentChecker.Missing> {
        val missing = withContext(Dispatchers.IO) {
            ComponentChecker.findMissing(activity, contentsManager, settings)
        }
        if (missing.isEmpty()) {
            withContext(Dispatchers.IO) { ConfigApplier.apply(shortcut, settings) }
        }
        return missing
    }
}
