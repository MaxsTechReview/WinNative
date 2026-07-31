package com.winlator.cmod.feature.retro

import android.os.Bundle
import android.system.Os
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.winlator.cmod.R
import com.winlator.cmod.shared.theme.WinNativeTheme
import org.love2d.sdl.SDLActivity
import java.io.File

/**
 * Hosts the LOVE engine inside WinNative, the way DolphinEmulationActivity hosts
 * Dolphin: the engine runs in this app's own process and WinNative keeps the
 * menus, the settings and the controls.
 *
 * The engine's own launcher never appears, and its own on-screen D-pad and
 * options menu are never used. What the player sees is WinNative's Game Boy pad
 * and WinNative's Retro drawer -- the same drawer the GB, GBC and GBA libretro
 * paths use, built from the same composable against the same controller.
 *
 * Two things make that possible and are worth knowing before changing anything
 * here:
 *
 *  - The drawer is not tied to libretro. RetroMenuController takes callbacks
 *    that return rows, so the rows can come from anywhere; this activity's come
 *    from the engine over [Gen1EngineBridge] rather than from a libretro core's
 *    variables.
 *
 *  - SDLActivity extends plain Activity, not ComponentActivity, so none of the
 *    owners Compose expects to find on the view tree exist. This activity
 *    supplies them itself; see the lifecycle plumbing below.
 */
class Gen1EngineActivity :
    org.love2d.android.GameActivity(),
    RetroInputView.Listener,
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {
    private var pad: RetroInputView? = null
    private lateinit var bridge: Gen1EngineBridge
    private val menu = RetroMenuController()
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // ------------------------------------------------------------- lifecycle
    //
    // ComposeView refuses to compose unless it can find a LifecycleOwner, a
    // ViewModelStoreOwner and a SavedStateRegistryOwner on its view tree.
    // ComponentActivity would provide all three, but SDL's activity predates it
    // and extends Activity directly -- and rebasing SDL's Java glue onto
    // ComponentActivity would be a much larger change to vendored upstream code
    // than implementing three small interfaces here.

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    override fun onSaveInstanceState(outState: Bundle) {
        savedStateController.performSave(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onResume() {
        super.onResume()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        schedulePoll()
    }

    override fun onStop() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        handler.removeCallbacksAndMessages(null)
        store.clear()
        super.onDestroy()
    }

    // ----------------------------------------------------------------- input

    /**
     * Which directions the D-pad is currently holding down. The pad reports an
     * analogue position on every move, but the engine wants key edges, so this
     * is diffed against the new position and only the changes are sent -- a
     * repeated "still holding up" must not re-issue a key-down, and rolling
     * from up to up-left has to press left without releasing up.
     */
    private val heldDirections = HashSet<Int>()

    /**
     * The engine reads SDL, and SDL translates Android key codes itself, so the
     * pad drives it by injecting the key codes gen1recomp binds in
     * src/core/Input.lua: z=A, x=B, escape=start, tab=select, arrows for the
     * D-pad. Enter is deliberately not used for start -- the engine binds
     * "return" to A.
     *
     * Note this is only ever called from the pad's own touch handling. The menu
     * does NOT reach the engine this way; it uses the bridge. Injecting keys
     * from the menu is what used to deadlock SDL.
     */
    private fun sendKey(keyCode: Int, down: Boolean) {
        runCatching {
            if (down) SDLActivity.onNativeKeyDown(keyCode) else SDLActivity.onNativeKeyUp(keyCode)
        }.onFailure { Log.w(TAG, "key inject failed: ${it.message}") }
    }

    override fun onButton(keyCode: Int, down: Boolean) {
        // While the drawer is open the pad belongs to the drawer, not the game
        // -- otherwise pressing A to pick a menu row also presses A in the game
        // behind it. This is what RetroActivity does on the libretro path.
        if (menu.visible) {
            menu.handleKey(
                keyCode,
                if (down) android.view.KeyEvent.ACTION_DOWN else android.view.KeyEvent.ACTION_UP,
            )
            return
        }
        val mapped = when (keyCode) {
            android.view.KeyEvent.KEYCODE_BUTTON_A -> android.view.KeyEvent.KEYCODE_Z
            android.view.KeyEvent.KEYCODE_BUTTON_B -> android.view.KeyEvent.KEYCODE_X
            android.view.KeyEvent.KEYCODE_BUTTON_START -> android.view.KeyEvent.KEYCODE_ESCAPE
            android.view.KeyEvent.KEYCODE_BUTTON_SELECT -> android.view.KeyEvent.KEYCODE_TAB
            else -> return
        }
        sendKey(mapped, down)
    }

    override fun onDpad(x: Float, y: Float) {
        if (menu.visible) {
            menu.handleAxis(x, y)
            return
        }
        // Deadzone keeps a resting thumb from chattering the direction keys.
        val wanted = HashSet<Int>(4)
        if (x <= -DPAD_DEADZONE) wanted.add(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        if (x >= DPAD_DEADZONE) wanted.add(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        if (y <= -DPAD_DEADZONE) wanted.add(android.view.KeyEvent.KEYCODE_DPAD_UP)
        if (y >= DPAD_DEADZONE) wanted.add(android.view.KeyEvent.KEYCODE_DPAD_DOWN)

        for (k in heldDirections - wanted) sendKey(k, false)
        for (k in wanted - heldDirections) sendKey(k, true)
        heldDirections.clear()
        heldDirections.addAll(wanted)
    }

    // The Game Boy has no analogue sticks; the layout does not draw them, and
    // an unexpected event must not be translated into a direction key.
    override fun onStick(x: Float, y: Float) = Unit

    override fun onRightStick(x: Float, y: Float) = Unit

    override fun onMenu() {
        runOnUiThread { openMenu() }
    }

    private fun openMenu() {
        // Release first so a direction held when the menu opened does not stay
        // down behind it, which is what the libretro path does too.
        releaseAllKeys()
        // The engine publishes state continuously, but reading once here means
        // the drawer opens showing current values instead of filling in a poll
        // later.
        bridge.refresh()
        menu.open()
        pollFaster()
    }

    private fun releaseAllKeys() {
        for (k in heldDirections) sendKey(k, false)
        heldDirections.clear()
    }

    /**
     * A hardware key -- a real controller, or the emulator's own keyboard.
     * Routed to the drawer when it is open for the same reason the pad is.
     */
    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (menu.visible && menu.handleKey(keyCode, android.view.KeyEvent.ACTION_DOWN)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: android.view.KeyEvent): Boolean {
        if (menu.visible && menu.handleKey(keyCode, android.view.KeyEvent.ACTION_UP)) return true
        return super.onKeyUp(keyCode, event)
    }

    @Deprecated("Activity, not ComponentActivity: there is no back dispatcher to register with.")
    override fun onBackPressed() {
        // Back closes the drawer a level at a time, then opens it -- matching
        // the libretro path, where back is how a player without a MENU button
        // reaches the menu at all.
        if (menu.visible) {
            menu.handleKey(android.view.KeyEvent.KEYCODE_BACK, android.view.KeyEvent.ACTION_UP)
        } else {
            openMenu()
        }
    }

    // ------------------------------------------------------------- menu model

    /**
     * The drawer's tabs for this path.
     *
     * Built here rather than through RetroDrawerTabs.build because the engine
     * does not have the same surfaces a libretro core does: there is no HUD to
     * configure (the FPS overlay reads libretro state), no netplay, and no
     * memory cards. A tab that opened onto nothing would be worse than an
     * absent one.
     */
    private fun buildTabs(): List<RetroTabSpec> =
        listOf(
            RetroTabSpec(null, RetroDrawerIcons.Play, getString(R.string.retro_tab_menu)),
            RetroTabSpec(
                RetroPane.DISPLAY,
                Icons.Outlined.Monitor,
                getString(R.string.retro_tab_display),
            ),
            RetroTabSpec(
                RetroPane.SOUND,
                Icons.AutoMirrored.Outlined.VolumeUp,
                getString(R.string.retro_tab_sound),
            ),
            RetroTabSpec(
                RetroPane.PERFORMANCE,
                Icons.Outlined.Bolt,
                getString(R.string.retro_ps2_tab_performance),
            ),
            RetroTabSpec(
                RetroPane.CONTROLS,
                Icons.Outlined.SportsEsports,
                getString(R.string.retro_tab_controls),
            ),
            RetroTabSpec(
                RetroPane.SYSTEM,
                Icons.Outlined.Tune,
                getString(R.string.retro_tab_system),
            ),
        )

    /**
     * Which pane an engine option row belongs on.
     *
     * The engine publishes a flat list -- its own OPTIONS menu is one long
     * column -- so the grouping is WinNative's, to match how every other system
     * in the app presents settings. Unknown ids deliberately fall through to
     * SYSTEM rather than being dropped: an upstream sync that adds a row must
     * make it reachable without a change here.
     */
    private fun paneForRow(id: String): RetroPane =
        when {
            Gen1EngineBridge.isModRow(id) -> RetroPane.DISPLAY
            id in SOUND_ROWS -> RetroPane.SOUND
            id in DISPLAY_ROWS -> RetroPane.DISPLAY
            id in PERFORMANCE_ROWS -> RetroPane.PERFORMANCE
            id in CONTROL_ROWS -> RetroPane.CONTROLS
            else -> RetroPane.SYSTEM
        }

    /** Engine rows for one pane, as drawer entries. */
    private fun engineRows(pane: RetroPane): List<RetroMenuEntry> {
        val rows = bridge.state.rows.filter { paneForRow(it.id) == pane }
        // The mod's rows lead the Display pane: they are what the player turned
        // 3D mode on for, and the engine's own display rows still follow.
        val ordered =
            if (pane == RetroPane.DISPLAY) {
                rows.filter { Gen1EngineBridge.isModRow(it.id) } +
                    rows.filterNot { Gen1EngineBridge.isModRow(it.id) }
            } else {
                rows
            }
        return ordered.map { row ->
            if (row.steppable) {
                RetroMenuEntry.Stepper(row.label, row.value) { direction ->
                    bridge.step(row.id, direction)
                    pollFaster()
                }
            } else {
                // An activate row opens one of the engine's own sub-screens
                // (its mod manager, its key rebinder). Those are engine UI, so
                // the drawer closes and hands the screen over rather than
                // drawing on top of it.
                RetroMenuEntry.Action(row.label, RetroDrawerIcons.EditLayout, subtitle = row.value) {
                    bridge.activate(row.id)
                    menu.close()
                }
            }
        }
    }

    private fun buildEntriesFor(pane: RetroPane?): List<RetroMenuEntry> =
        when (pane) {
            null -> buildMainEntries()
            RetroPane.SAVES -> buildSaveEntries()
            RetroPane.CONTROLS -> buildControlEntries() + engineRows(RetroPane.CONTROLS)
            else -> engineRows(pane)
        }

    private fun buildMainEntries(): List<RetroMenuEntry> =
        buildList {
            add(
                RetroMenuEntry.Action(getString(R.string.retro_lr_resume), RetroDrawerIcons.Resume) {
                    menu.close()
                },
            )
            // The engine owns its save slots -- it is not a libretro core, so
            // there is no state blob for WinNative to serialise. Saving writes
            // the engine's own save file, which is also what the player would
            // get from SAVE inside the game.
            add(
                RetroMenuEntry.Action(getString(R.string.retro_engine_save), RetroDrawerIcons.Save) {
                    bridge.saveGame()
                    menu.close()
                    toast(getString(R.string.retro_engine_saved))
                },
            )
            add(
                RetroMenuEntry.Action(
                    getString(R.string.retro_engine_slots),
                    RetroDrawerIcons.Load,
                    subtitle = bridge.state.slots.firstOrNull { it.active }?.name,
                ) { menu.showPane(RetroPane.SAVES) },
            )
            add(
                RetroMenuEntry.Action(getString(R.string.retro_lr_reset), RetroDrawerIcons.Reset) {
                    bridge.reset()
                    menu.close()
                },
            )
            add(
                RetroMenuEntry.Action(
                    getString(R.string.retro_lr_achievements),
                    RetroDrawerIcons.Achievements,
                    subtitle = getString(R.string.retro_engine_achievements_unavailable),
                ) {
                    // Stated plainly rather than opening an empty achievement
                    // list. This engine is a reimplementation, not an emulator:
                    // it never executes the ROM and has no Game Boy memory for
                    // RetroAchievements to watch, so nothing here can be
                    // tracked yet.
                    toast(getString(R.string.retro_engine_achievements_unavailable))
                },
            )
        }

    /**
     * The progress line under a save slot: play time, badges and Pokedex
     * count, whichever of them the engine could report, with the active slot
     * marked. An empty slot gets no line rather than a row of zeroes.
     */
    private fun slotSubtitle(slot: Gen1EngineBridge.Slot): String {
        val parts = mutableListOf<String>()
        if (slot.exists) {
            if (slot.playTime.isNotEmpty()) parts += slot.playTime
            if (slot.badges > 0) parts += resources.getQuantityString(R.plurals.retro_engine_badges, slot.badges, slot.badges)
            if (slot.caught > 0) parts += getString(R.string.retro_engine_caught, slot.caught)
        }
        if (slot.active) parts += getString(R.string.retro_engine_slot_active_only)
        return parts.joinToString(SUBTITLE_SEPARATOR)
    }

    private fun buildSaveEntries(): List<RetroMenuEntry> =
        buildList {
            val slots = bridge.state.slots
            slots.forEachIndexed { index, slot ->
                add(
                    RetroMenuEntry.SaveSlot(
                        slot = index,
                        title = slot.name,
                        subtitle = slotSubtitle(slot),
                        filled = slot.exists,
                        onClick = {
                            bridge.loadSlot(slot.id)
                            menu.close()
                        },
                        // The engine owns the slot files, so the rename goes
                        // through it rather than being written here. The
                        // drawer's own prompt collects the name, the same one
                        // the libretro path uses for save-state slots.
                        onRename = {
                            menu.renamePrompt =
                                RetroRenamePrompt(
                                    title = getString(R.string.retro_engine_rename_slot),
                                    initial = slot.name,
                                ) { entered ->
                                    val name = entered?.trim().orEmpty()
                                    if (name.isNotEmpty() && name != slot.name) {
                                        bridge.renameSlot(slot.id, name)
                                        pollFaster()
                                    }
                                }
                        },
                    ),
                )
            }
            if (slots.isEmpty()) {
                add(
                    RetroMenuEntry.Action(
                        getString(R.string.retro_engine_no_slots),
                        RetroDrawerIcons.Save,
                    ) {},
                )
            }
            add(
                RetroMenuEntry.Action(getString(R.string.retro_engine_new_slot), RetroDrawerIcons.Add) {
                    bridge.newSlot()
                    menu.close()
                },
            )
        }

    /**
     * WinNative's own control settings, which belong to the pad rather than to
     * the engine -- the engine never sees the pad, only the keys it sends.
     */
    private fun buildControlEntries(): List<RetroMenuEntry> =
        buildList {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this@Gen1EngineActivity)
            val haptic = prefs.getFloat(PREF_HAPTIC, DEFAULT_HAPTIC)
            add(
                RetroMenuEntry.Slider(
                    label = getString(R.string.retro_lr_haptic_feedback),
                    valueText = "${(haptic * 100).toInt()}%",
                    value = haptic,
                    min = 0f,
                    max = 1f,
                    step = 0.1f,
                ) { value ->
                    prefs.edit().putFloat(PREF_HAPTIC, value).apply()
                    pad?.hapticStrength = value
                    menu.rebuild()
                },
            )
        }

    private fun buildBottomEntries(): List<RetroMenuEntry.Action> =
        listOf(
            RetroMenuEntry.Action(getString(R.string.retro_lr_exit), RetroDrawerIcons.Exit, danger = true) {
                // Save before leaving, the way closing a game on the libretro
                // path writes its state: the engine's save is the only record
                // of progress on this path.
                bridge.saveGame()
                menu.close()
                handler.postDelayed({ finish() }, EXIT_SAVE_GRACE_MS)
            },
        )

    private fun toast(text: String) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    // --------------------------------------------------------------- polling

    /**
     * The engine publishes state on its own schedule, so the host polls.
     *
     * Slow while playing -- nothing is reading the values -- and fast while the
     * drawer is open or has just been touched, so a stepped row shows its new
     * value promptly. Rebuilding only when the sequence number moves keeps this
     * from churning the menu every tick.
     */
    private val poll =
        object : Runnable {
            override fun run() {
                if (bridge.refresh() && menu.visible) menu.rebuild()
                handler.postDelayed(this, if (menu.visible) POLL_ACTIVE_MS else POLL_IDLE_MS)
            }
        }

    private fun schedulePoll() {
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    private fun pollFaster() = schedulePoll()

    // ------------------------------------------------------------- SDL wiring

    /**
     * The engine ships in the retro bundle, not in the APK, so the default
     * System.loadLibrary (which only searches the APK's own library dir) cannot
     * find it. Load by absolute path out of the bundle instead -- the same
     * thing DolphinEmulationActivity does for libmain.so. Order matters: each
     * library here is linked against the ones above it.
     */
    override fun loadLibraries() {
        val dir = engineLibDir(this)
        for (lib in ENGINE_LIBS) {
            val so = File(dir, "lib$lib.so")
            if (!so.isFile) {
                throw UnsatisfiedLinkError("engine library missing from bundle: ${so.absolutePath}")
            }
            System.load(so.absolutePath)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must precede any lifecycle event, and the ON_CREATE below must follow
        // it -- SavedStateRegistry enforces that order.
        savedStateController.performRestore(savedInstanceState)

        val rom = intent.getStringExtra(EXTRA_ROM_PATH)
        val version = intent.getStringExtra(EXTRA_VERSION)
        Log.i(TAG, "onCreate rom=$rom version=$version")

        // Before super.onCreate, because that starts the engine and the engine
        // discovers its mods once, during load. Installing afterwards would
        // take until the next launch to show up.
        Gen1ModInstaller.ensureInstalled(this)

        bridge = Gen1EngineBridge(this)
        // Anything left from the last run describes a game that is no longer
        // loaded, and the drawer would show it for the second or so before the
        // engine publishes fresh state.
        bridge.clearStale()

        // Belt and braces only. A file-written probe inside love.load showed
        // os.getenv returning nil for both of these, so the process environment
        // is NOT the channel the engine reads -- the real handoff is the command
        // line below. This stays because it costs nothing and keeps anything
        // that does read the environment consistent with what the engine was
        // told. Calling SDL's nativeSetenv instead was tried and is worse: it is
        // only bound after super.onCreate, and calling it there made the activity
        // exit before love.load ever ran.
        runCatching {
            if (!rom.isNullOrEmpty()) Os.setenv("POKEPORT_IMPORT_ROM", rom, true)
            if (!version.isNullOrEmpty()) Os.setenv("POKEPORT_VERSION", version, true)
        }.onFailure { Log.w(TAG, "could not set engine environment: ${it.message}") }

        super.onCreate(savedInstanceState)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        menu.entriesProvider = { pane -> buildEntriesFor(pane) }
        menu.bottomProvider = { buildBottomEntries() }
        menu.tabs = buildTabs()

        // The owners have to be on the window ROOT, not just on the ComposeView.
        // Compose creates its recomposer per window and looks the lifecycle
        // owner up from the root view, so setting them only on the ComposeView
        // still throws "ViewTreeLifecycleOwner not found" the moment it attaches.
        // ComponentActivity does this on the decor view too, which is exactly
        // what this stands in for.
        window.decorView.let { root ->
            root.setViewTreeLifecycleOwner(this)
            root.setViewTreeViewModelStoreOwner(this)
            root.setViewTreeSavedStateRegistryOwner(this)
        }

        // Both the pad and the drawer go into SDL's own layout rather than
        // through addContentView: SDL builds its surface inside mLayout and sets
        // that as the content view, so a view added to the activity's content
        // frame instead lands outside the tree SDL manages -- which blanked the
        // engine's rendering entirely.
        val host = mLayout
        if (host == null) {
            Log.w(TAG, "SDL layout missing; pad and menu not attached")
            return
        }

        pad = RetroInputView(this, this, RetroSystems.GAMEBOY).also { view ->
            // The same setup RetroActivity does. setGameArea is the one that
            // matters most: the pad lays its buttons out around the game
            // rectangle, so without an area it renders nothing at all.
            view.loadStickInversion()
            view.hapticStrength =
                androidx.preference.PreferenceManager
                    .getDefaultSharedPreferences(this)
                    .getFloat(PREF_HAPTIC, DEFAULT_HAPTIC)
            view.setCustomColors(RetroControlLayouts.loadColors(this, RetroSystems.GAMEBOY.id))

            host.addView(
                view,
                android.widget.RelativeLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            host.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                updateGameArea(host, view)
            }
            host.post { updateGameArea(host, view) }
        }

        host.addView(
            ComposeView(this).apply {
                // Above both SDL's surface and the pad, so the drawer is not
                // drawn underneath the buttons it is replacing.
                elevation = MENU_ELEVATION
                setViewTreeLifecycleOwner(this@Gen1EngineActivity)
                setViewTreeViewModelStoreOwner(this@Gen1EngineActivity)
                setViewTreeSavedStateRegistryOwner(this@Gen1EngineActivity)
                setContent {
                    WinNativeTheme {
                        Box(Modifier.fillMaxSize()) {
                            RetroDrawerMenu(menu)
                        }
                    }
                }
            },
            android.widget.RelativeLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    /**
     * Where the Game Boy picture sits inside the window, which is what the pad
     * arranges itself around. Mirrors RetroActivity.updateOverlayArea with its
     * overlayPush() of 0: the picture keeps its aspect and is centred in
     * landscape, top-aligned in portrait so the buttons get the space below it.
     */
    private fun updateGameArea(host: android.view.ViewGroup, view: RetroInputView) {
        val w = host.width
        val h = host.height
        if (w <= 0 || h <= 0) return
        val portrait = h >= w

        // Sized to a whole multiple of the Game Boy's 160x144 rather than to
        // whatever fits exactly.
        //
        // The engine scales by an integer factor and centres what is left over
        // (Renderer:fitScale is a floor), so handing it a rectangle that is not
        // a multiple of 160x144 buys nothing: it draws the same picture and
        // pads the difference with its own black border. Rounding down here
        // moves that border outside the surface, which means the picture sits
        // flush against the top of the screen in portrait instead of floating
        // below a black band, and the pad gets the leftover height rather than
        // the engine wasting it.
        val budgetHeight = if (portrait) (h * PORTRAIT_GAME_HEIGHT_FRACTION).toInt() else h
        val scale = minOf(w / GB_WIDTH, budgetHeight / GB_HEIGHT).coerceAtLeast(1)
        val gameWidth = (GB_WIDTH * scale).toFloat()
        val gameHeight = (GB_HEIGHT * scale).toFloat()

        // Centred across, and in portrait pushed to the top so every pixel the
        // picture does not use goes to the buttons underneath it.
        val left = (w - gameWidth) * 0.5f
        val top = if (portrait) 0f else (h - gameHeight) * 0.5f
        val area = android.graphics.RectF(left, top, left + gameWidth, top + gameHeight)

        view.setGameArea(area)
        applySurfaceBounds(area)
    }

    /**
     * Puts the engine's own surface exactly where the pad reserved space for it.
     *
     * SDL adds its surface to mLayout with no layout parameters, so it fills the
     * whole window and LOVE letterboxes the 160x144 picture inside that. The pad,
     * meanwhile, lays its buttons out around the rectangle it was given -- so the
     * two disagreed: the picture floated in the middle of the window with a black
     * band above it and its lower part behind the button panel.
     *
     * Rather than trying to predict where LOVE will letterbox and matching the pad
     * to it, the surface is given the pad's rectangle. Its aspect already matches
     * the Game Boy's, so LOVE has no letterboxing left to do and the two cannot
     * drift apart.
     */
    private fun applySurfaceBounds(area: android.graphics.RectF) {
        val want =
            android.graphics.Rect(
                area.left.toInt(),
                area.top.toInt(),
                area.right.toInt(),
                area.bottom.toInt(),
            )
        if (want.isEmpty || want == surfaceBounds) return
        val surface = mSurface ?: return
        // Cached and compared because assigning layout parameters requests
        // another layout pass, which calls straight back into here.
        surfaceBounds = want
        surface.layoutParams =
            android.widget.RelativeLayout.LayoutParams(want.width(), want.height()).apply {
                leftMargin = want.left
                topMargin = want.top
            }
    }

    private var surfaceBounds: android.graphics.Rect? = null

    override fun onPause() {
        // A key held when the activity goes away would otherwise stay down in
        // the engine and keep the player walking on return.
        releaseAllKeys()
        handler.removeCallbacks(poll)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onPause()
    }

    /**
     * SDL hands this straight to LOVE as argv, and LOVE puts argv into Lua's
     * `arg`, so this is a channel the engine demonstrably reads -- gen1recomp's
     * own conf.lua already parses `arg` for --editor. The engine side pairs with
     * this in scripts/winnative_boot_args.sh, which teaches main.lua to accept
     * these two flags alongside the POKEPORT_* environment variables it already
     * supports.
     */
    override fun getArguments(): Array<String> {
        val rom = intent?.getStringExtra(EXTRA_ROM_PATH)
        val version = intent?.getStringExtra(EXTRA_VERSION)
        val args = ArrayList<String>(4)
        if (!rom.isNullOrEmpty()) { args.add("--import-rom"); args.add(rom) }
        if (!version.isNullOrEmpty()) { args.add("--game-version"); args.add(version) }
        // Nothing here turns off the engine's own on-screen D-pad: passing a
        // flag for it (either "--touch-controls 0" or "--touch-controls=0")
        // made LOVE die silently just after SDL_main, so argv is kept to the
        // two settings that are known to survive it. The overlay is suppressed
        // on the engine side instead, by scripts/winnative_boot_args.sh, which
        // is the better place for it anyway: this fork is only ever hosted by
        // WinNative, and WinNative always supplies its own pad.
        Log.i(TAG, "engine argv: $args")
        return args.toTypedArray()
    }

    companion object {
        private const val TAG = "WnGen1Engine"

        const val EXTRA_ROM_PATH = "wn.engine.rom"
        const val EXTRA_VERSION = "wn.engine.version"
        const val EXTRA_GAME_NAME = "wn.engine.game_name"
        const val EXTRA_SHORTCUT_PATH = "wn.engine.shortcut"

        private const val DPAD_DEADZONE = 0.35f

        private const val PREF_HAPTIC = "retro_haptic_strength"
        private const val DEFAULT_HAPTIC = 0.4f

        /** Above SDL's surface and the pad. */
        private const val MENU_ELEVATION = 2000f

        /** Between the parts of a save slot's progress line. */
        private const val SUBTITLE_SEPARATOR = "  \u00b7  "

        private const val POLL_IDLE_MS = 700L
        private const val POLL_ACTIVE_MS = 200L

        /** Long enough for the engine to pick the save command up and run it. */
        private const val EXIT_SAVE_GRACE_MS = 400L

        // Engine option row ids, grouped the way WinNative presents settings.
        // Anything not listed lands on System; see paneForRow.
        private val SOUND_ROWS = setOf("musicVol", "sfxVol", "pikaVol", "musicFilter")
        private val DISPLAY_ROWS =
            setOf("colors", "tilt", "gbcfx", "zoom", "voidFill", "videoMode", "animations")
        private val PERFORMANCE_ROWS = setOf("fpsCap", "speed")
        private val CONTROL_ROWS = setOf("controls")

        /** The Game Boy's screen, which is also the engine's UI surface size. */
        private const val GB_WIDTH = 160
        private const val GB_HEIGHT = 144

        /**
         * Most of the height the picture may take in portrait, leaving the rest
         * for the buttons. Only ever reduces the scale factor, so on a tall
         * screen the picture stops growing before it crowds the pad out.
         */
        private const val PORTRAIT_GAME_HEIGHT_FRACTION = 0.6f

        /** Dependency order; liblove links against the three above it. */
        private val ENGINE_LIBS = listOf("c++_shared", "mpg123", "openal", "love")

        fun engineDir(context: android.content.Context): File =
            File(RetroBundle.root(context), "data/gen1recomp")

        fun engineLibDir(context: android.content.Context): File =
            File(engineDir(context), "lib")

        /** The Lua engine archive the GameActivity is pointed at via the Intent. */
        fun gameArchive(context: android.content.Context): File =
            File(engineDir(context), "game.love")

        fun isInstalled(context: android.content.Context): Boolean =
            gameArchive(context).isFile &&
                ENGINE_LIBS.all { File(engineLibDir(context), "lib$it.so").isFile }
    }
}
