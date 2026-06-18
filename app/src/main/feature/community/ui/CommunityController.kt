package com.winlator.cmod.feature.community.ui

import android.app.Activity
import android.app.Dialog
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Orchestrates the community Download/Upload features for the current shortcut.
 * Owns the API client (bound to the opaque uploader handle), hosts the download
 * dialog, runs uploads, and gates apply behind the MISSING-COMPONENT check.
 */
class CommunityController(
    private val activity: Activity,
    private val shortcut: Shortcut,
    private val contentsManager: ContentsManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val api = CommunityApiClient(activity)
    private var downloadDialog: Dialog? = null

    /** Set by the host so the open settings UI reloads after a config is applied. */
    var onConfigApplied: () -> Unit = {}

    init {
        // Best-effort upgrade of the handle to the Google account, if signed in.
        UploaderIdentity.resolveGoogle(activity)
    }

    /** Upload the (already-saved) current shortcut as a community config. */
    fun upload() {
        ensureGoogle {
            // Immediate feedback that the tap registered (the upload is async).
            toast("Uploading…")
            scope.launch {
                runCatching {
                    val settings = ConfigSerializer.serialize(shortcut)
                    val gameKey = ConfigSerializer.gameKey(shortcut)
                    val store = ConfigSerializer.storeOf(shortcut)
                    val hw = DeviceIdentity.current()
                    api.upload(gameKey, store, settings, hw)
                }.onSuccess { toast("Upload successful") }
                    .onFailure {
                        toast("Upload failed: ${it.message ?: "unknown error"}")
                    }
            }
        }
    }

    /**
     * Ensure a Google-backed identity (required for upload/vote so that bans can
     * be tied to a Google account), prompting Play Games sign-in if needed, then
     * run [action]. Shows a clear message if the user declines.
     */
    fun ensureGoogle(action: () -> Unit) {
        if (UploaderIdentity.isGoogleBacked()) {
            action()
            return
        }
        toast("Sign in with a Google account…")
        UploaderIdentity.signInAndResolve(activity) { ok ->
            if (ok) action()
            else toast("Google sign-in is required to upload or vote")
        }
    }

    /** Cancel in-flight work. Call when the host dialog is dismissed. */
    fun dispose() {
        scope.cancel()
    }

    /**
     * System toast for user feedback. Used instead of the app's WinToast because
     * WinToast anchors its popup to the Activity window, which is occluded by the
     * Shortcut-Settings dialog; a system toast always renders on top.
     */
    private fun toast(msg: String) {
        activity.runOnUiThread { Toast.makeText(activity, msg, Toast.LENGTH_LONG).show() }
    }

    /** Open the community config browser for this game. */
    fun openDownload() {
        val lifecycleOwner = activity as? LifecycleOwner
        val savedStateOwner = activity as? SavedStateRegistryOwner
        if (lifecycleOwner == null || savedStateOwner == null) {
            toast("Community sharing is unavailable here")
            return
        }
        val gameKey = ConfigSerializer.gameKey(shortcut)
        val hw = DeviceIdentity.current()
        val dialog = Dialog(activity, R.style.ContentDialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setCanceledOnTouchOutside(false)
            setOwnerActivity(activity)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setGravity(android.view.Gravity.CENTER)
                setDimAmount(0.5f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
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
                    )
                }
            }
        }
        dialog.setContentView(composeView)
        dialog.show()
        // CRITICAL: the floating ContentDialog ignores setLayout() called BEFORE
        // show() (it wraps content and squishes). Size it AFTER show, and again
        // post-attach — exactly how ShortcutSettingsComposeDialog sizes itself.
        sizeToHost(dialog)
        dialog.window?.decorView?.post { sizeToHost(dialog) }
    }

    /** Size a dialog window to ~the host activity's window (like Shortcut Settings). */
    private fun sizeToHost(dialog: Dialog) {
        val host = activity.window.decorView
        val w = (if (host.width > 0) host.width
                 else activity.resources.displayMetrics.widthPixels)
        val h = (if (host.height > 0) host.height
                 else activity.resources.displayMetrics.heightPixels)
        dialog.window?.setLayout((w * 0.96f).toInt(), (h * 0.92f).toInt())
        dialog.window?.setGravity(android.view.Gravity.CENTER)
    }

    /**
     * Open the full Shortcut-Settings UI in PREVIEW mode for a fetched config.
     * On Apply, it writes to the real shortcut, reloads the open settings UI, and
     * closes the community browser.
     */
    private fun openPreview(settings: JSONObject) {
        com.winlator.cmod.feature.shortcuts.ShortcutSettingsComposeDialog.preview(
            activity, shortcut, settings,
        ) {
            onConfigApplied()
            downloadDialog?.dismiss()
        }
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
