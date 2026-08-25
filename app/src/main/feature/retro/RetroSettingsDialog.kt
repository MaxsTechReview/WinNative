package com.winlator.cmod.feature.retro

import android.app.Activity
import android.app.Dialog
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import com.winlator.cmod.feature.library.GameSettingsNav
import com.winlator.cmod.feature.shortcuts.LibraryShortcutArtwork
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.shared.android.ImageUtils
import com.winlator.cmod.shared.io.FileUtils
import com.winlator.cmod.shared.theme.WinNativeTheme
import com.winlator.cmod.shared.ui.nav.PANE_DIR_ACTIVATE
import com.winlator.cmod.shared.ui.nav.PaneNavWindowHandlers
import com.winlator.cmod.shared.ui.nav.bindPaneNav
import com.winlator.cmod.shared.ui.toast.WinToast

class RetroSettingsDialog(
    private val activity: Activity,
    private val shortcut: Shortcut,
) {
    private val state = RetroSettingsState(shortcut, activity)
    private val nav = GameSettingsNav()
    private var restorePaneNav: (() -> Unit)? = null
    private var pendingArtworkSlot = LibraryShortcutArtwork.LibraryArtworkSlot.GAME_CARD

    private val artworkPickerLauncher: ActivityResultLauncher<Array<String>>? =
        (activity as? ComponentActivity)?.activityResultRegistry?.register(
            "retro_artwork_picker",
            ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) saveSelectedArtwork(uri)
        }

    private val biosPickerLauncher: ActivityResultLauncher<Array<String>>? =
        (activity as? ComponentActivity)?.activityResultRegistry?.register(
            "retro_bios_picker",
            ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                RetroBiosImport.importFromUri(activity, uri)
                    .onSuccess {
                        Toast.makeText(activity, activity.getString(R.string.retro_scr_bios_imported, it), Toast.LENGTH_SHORT).show()
                        state.biosRefresh++
                    }
                    .onFailure {
                        Toast.makeText(activity, it.message ?: activity.getString(R.string.retro_scr_invalid_bios_file), Toast.LENGTH_LONG).show()
                    }
            }
        }

    private val hddPickerLauncher: ActivityResultLauncher<Array<String>>? =
        (activity as? ComponentActivity)?.activityResultRegistry?.register(
            "retro_hdd_picker",
            ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            if (uri != null) {
                state.hddImporting = true
                Toast.makeText(activity, activity.getString(R.string.retro_scr_importing), Toast.LENGTH_SHORT).show()
                Thread {
                    val result = RetroHddImport.importFromUri(activity, uri)
                    activity.runOnUiThread {
                        state.hddImporting = false
                        result
                            .onSuccess { name ->
                                state.hddImage = name
                                state.hddRefresh++
                                Toast.makeText(activity, activity.getString(R.string.retro_scr_hdd_imported, name), Toast.LENGTH_LONG).show()
                            }
                            .onFailure {
                                Toast.makeText(activity, it.message ?: activity.getString(R.string.retro_scr_invalid_hdd_file), Toast.LENGTH_LONG).show()
                            }
                    }
                }.start()
            }
        }

    private val cheatFilePickerLauncher: ActivityResultLauncher<Array<String>>? =
        (activity as? ComponentActivity)?.activityResultRegistry?.register(
            "retro_cheat_file_picker",
            ActivityResultContracts.OpenDocument(),
        ) { uri: Uri? ->
            val serial = state.gameSerial
            if (uri != null && serial != null) {
                val isPatch = state.pendingImportIsPatch
                Thread {
                    val text = runCatching { activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                    val imported = if (text != null) parsePnachFile(text, if (isPatch) "patches" else "custom") else emptyList()
                    activity.runOnUiThread {
                        if (imported.isNotEmpty()) {
                            val existing = Ps2CheatStaging.read(activity, serial, isPatch)
                            val merged = existing.filterNot { e -> imported.any { it.name == e.name } } + imported
                            Ps2CheatStaging.write(activity, serial, isPatch, serial, merged)
                            state.cheatsRefresh++
                            Toast.makeText(activity, activity.getString(R.string.retro_scr_hdd_imported, imported.first().name), Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(activity, activity.getString(R.string.retro_scr_cheat_invalid), Toast.LENGTH_LONG).show()
                        }
                    }
                }.start()
            }
        }

    init {
        state.requestImportHdd = { hddPickerLauncher?.launch(arrayOf("*/*")) }
        state.requestImportCheatFile = { isPatch ->
            state.pendingImportIsPatch = isPatch
            cheatFilePickerLauncher?.launch(arrayOf("*/*"))
        }
    }

    private fun saveSelectedArtwork(uri: Uri) {
        val bitmap = ImageUtils.getBitmapFromUri(activity, uri, 1024)
        if (bitmap == null) {
            WinToast.show(activity, R.string.shortcuts_library_artwork_failed, Toast.LENGTH_SHORT)
            return
        }
        val slot = pendingArtworkSlot
        val previousPath = shortcut.getExtra(slot.extraKey)
        val outputFile = LibraryShortcutArtwork.buildManagedViewArtworkFile(activity, shortcut, slot)
        val system = RetroShortcuts.systemForShortcut(shortcut)
        val saved =
            if (system != null && RetroBoxart.caseArtEnabled(activity) && slot != LibraryShortcutArtwork.LibraryArtworkSlot.GAME_CARD) {
                RetroBoxart.composeCustom(activity, system, shortcut.getExtra("custom_name", shortcut.name), bitmap, outputFile)
            } else {
                FileUtils.saveBitmapToFile(bitmap, outputFile)
            }
        if (!saved) {
            WinToast.show(activity, R.string.shortcuts_library_artwork_failed, Toast.LENGTH_SHORT)
            return
        }
        if (previousPath.isNotBlank() && previousPath != outputFile.absolutePath) {
            LibraryShortcutArtwork.deleteManagedArtwork(activity, previousPath)
        }
        shortcut.putExtra(slot.extraKey, outputFile.absolutePath)
        shortcut.saveData()
        state.syncArtwork()
    }

    private fun clearArtwork(slot: LibraryShortcutArtwork.LibraryArtworkSlot) {
        LibraryShortcutArtwork.deleteManagedArtwork(activity, shortcut.getExtra(slot.extraKey))
        shortcut.putExtra(slot.extraKey, null)
        shortcut.saveData()
        state.syncArtwork()
    }

    private val dialog: Dialog =
        Dialog(activity, R.style.ContentDialog).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCancelable(true)
            setCanceledOnTouchOutside(false)
            setOwnerActivity(activity)
            window?.apply {
                setBackgroundDrawableResource(android.R.color.transparent)
                setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                )
                setDimAmount(0.5f)
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    isNavigationBarContrastEnforced = false
                }
            }
            setOnDismissListener {
                restorePaneNav?.invoke()
                restorePaneNav = null
            }
        }

    init {
        val composeView =
            ComposeView(activity).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                setViewTreeLifecycleOwner(activity as LifecycleOwner)
                setViewTreeSavedStateRegistryOwner(activity as SavedStateRegistryOwner)
                setContent {
                    WinNativeTheme {
                        val defaultDensity = LocalDensity.current
                        CompositionLocalProvider(
                            LocalDensity provides Density(defaultDensity.density, fontScale = 1f),
                        ) {
                            RetroGameSettingsContent(
                                state = state,
                                nav = nav,
                                onPickArtwork = { slot ->
                                    pendingArtworkSlot = slot
                                    artworkPickerLauncher?.launch(arrayOf("image/*"))
                                },
                                onRemoveArtwork = { slot -> clearArtwork(slot) },
                                onImportBios = { biosPickerLauncher?.launch(arrayOf("*/*")) },
                                onSave = {
                                    state.save()
                                    dialog.dismiss()
                                },
                                onCancel = { dialog.dismiss() },
                            )
                        }
                    }
                }
            }
        dialog.setContentView(composeView)
        (activity as LifecycleOwner).lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    if (dialog.isShowing) dialog.dismiss()
                }
            },
        )
    }

    fun show() {
        dialog.show()
        restorePaneNav?.invoke()
        restorePaneNav =
            dialog.window?.bindPaneNav(
                PaneNavWindowHandlers(
                    onDir = { nav.dpad(it) },
                    onActivate = { nav.dpad(PANE_DIR_ACTIVATE) },
                    onDismiss = { if (nav.onContentBack?.invoke() != true) dialog.dismiss() },
                    onStart = { nav.onSave?.invoke() },
                ),
            )
        dialog.window?.apply {
            applyDialogLayout()
            decorView.post { applyDialogLayout() }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val params = attributes
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
                params.blurBehindRadius = 10
                attributes = params
            }
        }
    }

    private fun Window.applyDialogLayout() {
        val dm = activity.resources.displayMetrics
        val hostView = activity.window?.decorView
        val hostWidth = hostView?.width?.takeIf { it > 0 } ?: dm.widthPixels
        val hostHeight = hostView?.height?.takeIf { it > 0 } ?: dm.heightPixels
        val screenWidthDp = hostWidth / dm.density
        val needsNearFullWidth = screenWidthDp < 820f
        val widthFactor = if (needsNearFullWidth) 0.96f else 0.88f
        val heightFactor = if (needsNearFullWidth) 0.90f else 0.88f
        setLayout((hostWidth * widthFactor).toInt(), (hostHeight * heightFactor).toInt())
    }
}
