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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.winlator.cmod.runtime.container.ContainerManager
import com.winlator.cmod.runtime.container.Shortcut
import com.winlator.cmod.runtime.display.ui.FrameRating
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
    private var menuView: ComposeView? = null
    private lateinit var bridge: Gen1EngineBridge

    /**
     * Whether the on-screen pad is drawn. When it is off the picture gets the
     * whole screen, which is the point of turning it off -- a player on a
     * controller should not keep a Game Boy-shaped letterbox.
     */
    private var touchControls = true

    /** Cached so the shortcut file is read once rather than on every write. */
    private var persistShortcut: Shortcut? = null

    /**
     * True until the engine reports a booted game.
     *
     * Held as Compose state so the loading screen can come down the moment the
     * game is up. It starts true because the engine takes a moment to publish
     * anything at all, and the first thing on screen would otherwise be the
     * engine's own splash -- the thing the loading screen exists to cover.
     */
    private var loadingVisible by androidx.compose.runtime.mutableStateOf(true)
    private var importState by
        androidx.compose.runtime.mutableStateOf<Gen1EngineBridge.Import?>(null)
    /**
     * The shortcut's cover, decoded off the main thread. Compose state because
     * it arrives after the loading screen is already up.
     */
    private var artwork by
        androidx.compose.runtime.mutableStateOf<android.graphics.Bitmap?>(null)

    /**
     * Which job the slot list is doing when it opens.
     *
     * Save and Load both land on the same list of slots, the way the games
     * themselves work -- you choose a slot to save into, and you choose a slot
     * to load from. One list, two jobs, so it has to be told which.
     */
    private enum class SlotAction { SAVE, LOAD }

    private var slotAction = SlotAction.LOAD
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
        bridge.startPolling(::onEngineState)
    }

    override fun onStop() {
        // The catch-all for a save the host never saw: the player using the
        // game's own SAVE menu writes a slot file with nothing on this side
        // being told. Staging on the way out picks that up whatever route the
        // save took. Cheap when nothing changed -- the fingerprint check drops
        // an unchanged set before any upload is queued.
        queueCloudBackup()
        // Nothing is on screen to show the state, so stop reading it. The
        // thread stays alive: a command can still be queued from a lifecycle
        // path, and the engine keeps running behind us.
        bridge.stopPolling()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onStop()
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        handler.removeCallbacksAndMessages(null)
        bridge.shutdown()
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
        // Built from the last state the bridge read, which is at most one idle
        // poll old. Reading the file here instead would put storage work in the
        // middle of the frame that opens the drawer, to save a couple of
        // hundred milliseconds on values that rarely change while playing --
        // and pollFaster below closes that gap anyway.
        menu.open()
        pollFaster()
    }

    private fun releaseAllKeys() {
        for (k in heldDirections) sendKey(k, false)
        heldDirections.clear()
    }

    /**
     * A hardware key -- a real controller, the emulator's keyboard, or the
     * system Back gesture.
     *
     * Intercepted at dispatch, not in onKeyDown. SDL's surface holds focus and
     * is its own OnKeyListener, so it consumes every key before the activity's
     * onKeyDown or onBackPressed would ever run -- which is why Back was
     * reaching the engine and opening its in-game menu instead of WinNative's.
     * dispatchKeyEvent is the one point that sees the event first.
     *
     * While the drawer is open every key belongs to it, for the same reason the
     * pad does: a press meant for the menu must not also reach the game behind
     * it.
     */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val keyCode = event.keyCode
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            // Acted on the up event; the down is swallowed so auto-repeat
            // cannot toggle the drawer open and shut.
            if (event.action == android.view.KeyEvent.ACTION_UP) {
                // Inside the drawer Back steps back a pane and then closes it;
                // outside it, Back is how a player with no MENU button opens it.
                if (menu.visible) {
                    menu.handleKey(keyCode, android.view.KeyEvent.ACTION_UP)
                } else {
                    openMenu()
                }
            }
            return true
        }
        if (menu.visible) {
            menu.handleKey(keyCode, event.action)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    // ------------------------------------------------------------- menu model

    /**
     * The drawer's tabs for this path.
     *
     * Built here rather than through RetroDrawerTabs.build because the engine
     * does not have every surface a libretro core does: there is no netplay and
     * there are no memory cards, and a tab that opened onto nothing would be
     * worse than an absent one. Everything else a Game Boy game offers is here,
     * and the HUD and Controls panes are the same ones the libretro path builds.
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
                RetroPane.HUD,
                RetroDrawerIcons.Hud,
                getString(R.string.retro_tab_hud),
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

    /**
     * One engine option row as a drawer entry.
     *
     * A row the engine could describe the whole value ladder for becomes a
     * dropdown, which is how every other system's settings are presented in
     * this app: the player sees what the choices are instead of discovering
     * them by pressing an arrow repeatedly. A row it could not -- an option
     * added upstream that the bridge has no ladder for -- keeps the arrows,
     * so it still works rather than disappearing.
     */
    private fun rowEntry(row: Gen1EngineBridge.Row): RetroMenuEntry =
        when {
            row.values.isNotEmpty() ->
                RetroMenuEntry.Choice(row.label, row.values, row.selectedIndex) { index ->
                    bridge.setRow(row.id, index)
                    pollFaster()
                }
            row.steppable ->
                RetroMenuEntry.Stepper(row.label, row.value) { direction ->
                    bridge.step(row.id, direction)
                    pollFaster()
                }
            else ->
                // An activate row opens one of the engine's own sub-screens
                // (its mod manager, its key rebinder). Those are engine UI, so
                // the drawer closes and hands the screen over rather than
                // drawing on top of it.
                RetroMenuEntry.Action(row.label, RetroDrawerIcons.EditLayout, subtitle = row.value) {
                    bridge.activate(row.id)
                    menu.close()
                }
        }

    /**
     * A two-value engine row as one half of a paired on/off row.
     *
     * Which end of the ladder counts as "on" is the row's, not a guess from the
     * label: the engine may translate ON and OFF, but the order it lists them
     * in is fixed by the row itself. The state under the label is the engine's
     * own word for it (ON, BORDERLESS) rather than a generic Enabled, because
     * that is what the same setting says everywhere else in the engine.
     */
    private fun toggleHalf(
        row: Gen1EngineBridge.Row,
        label: String,
        onIndex: Int,
    ): RetroMenuEntry.Toggle {
        val offIndex = if (onIndex == 0) 1 else 0
        return RetroMenuEntry.Toggle(
            label = label,
            subtitle = row.values.getOrNull(row.selectedIndex) ?: row.value,
            checked = row.selectedIndex == onIndex,
        ) { wanted ->
            bridge.setRow(row.id, if (wanted) onIndex else offIndex)
            pollFaster()
        }
    }

    /** Engine rows for one pane, as drawer entries. */
    private fun engineRows(pane: RetroPane): List<RetroMenuEntry> {
        val rows =
            bridge.state.rows.filter { paneForRow(it.id) == pane && it.id !in HIDDEN_ROWS }
        // The mod's rows lead the Display pane: they are what the player turned
        // 3D mode on for, and the engine's own display rows still follow.
        val ordered =
            if (pane == RetroPane.DISPLAY) {
                rows.filter { Gen1EngineBridge.isModRow(it.id) } +
                    rows.filterNot { Gen1EngineBridge.isModRow(it.id) }
            } else {
                rows
            }

        // BATTLE ANIMATION and VIDEO MODE are the only two rows here that are
        // genuinely on-or-off, and a dropdown of two values is a worse control
        // than a button. They share a row: VIDEO MODE keeps its place in the
        // ladder order, and BATTLE ANIMATION -- which comes first -- is where
        // the pair sits.
        val animations = ordered.firstOrNull { it.id == ANIMATIONS_ROW }?.takeIf { it.values.size == 2 }
        val videoMode = ordered.firstOrNull { it.id == VIDEO_MODE_ROW }?.takeIf { it.values.size == 2 }
        val pair =
            if (animations != null && videoMode != null) {
                RetroMenuEntry.TogglePair(
                    left = toggleHalf(animations, animations.label, onIndex = 0),
                    // VideoMode.MODES is { windowed, borderless }, so the
                    // second entry is the fullscreen one.
                    right = toggleHalf(videoMode, getString(R.string.retro_engine_fullscreen), onIndex = 1),
                )
            } else {
                null
            }

        return buildList {
            for (row in ordered) {
                when {
                    pair != null && row.id == ANIMATIONS_ROW -> add(pair)
                    pair != null && row.id == VIDEO_MODE_ROW -> Unit
                    else -> add(rowEntry(row))
                }
            }
        }
    }

    private fun buildEntriesFor(pane: RetroPane?): List<RetroMenuEntry> =
        when (pane) {
            null -> buildMainEntries()
            RetroPane.SAVES -> buildSaveEntries()
            RetroPane.CONTROLS -> buildControlEntries() + engineRows(RetroPane.CONTROLS)
            RetroPane.HUD -> buildHudEntries()
            else -> engineRows(pane)
        }

    private fun buildMainEntries(): List<RetroMenuEntry> =
        buildList {
            // Save and Load each open the slot list, which is how saving works
            // in these games and how save states work on the libretro path.
            // The engine owns the slots -- it is not a libretro core, so there
            // is no state blob for WinNative to serialise; picking a slot tells
            // the engine to write its own save there.
            val activeSlot = bridge.state.slots.firstOrNull { it.active }?.name
            add(
                RetroMenuEntry.Action(
                    getString(R.string.retro_engine_save),
                    RetroDrawerIcons.Save,
                    subtitle = activeSlot,
                ) {
                    slotAction = SlotAction.SAVE
                    menu.showPane(RetroPane.SAVES)
                },
            )
            add(
                RetroMenuEntry.Action(
                    getString(R.string.retro_engine_load),
                    RetroDrawerIcons.Load,
                    subtitle = activeSlot,
                ) {
                    slotAction = SlotAction.LOAD
                    menu.showPane(RetroPane.SAVES)
                },
            )
            add(
                // Drives the engine's own speed setting rather than a host-side
                // frame skip, so audio keeps its pitch -- the engine scales its
                // logic step and deliberately leaves sound alone.
                RetroMenuEntry.Action(
                    getString(R.string.retro_lr_fast_forward),
                    RetroDrawerIcons.FastForward,
                    active = bridge.state.fastForward,
                ) {
                    bridge.setFastForward(!bridge.state.fastForward)
                    pollFaster()
                },
            )
            add(
                RetroMenuEntry.Action(
                    getString(R.string.retro_lr_hud),
                    RetroDrawerIcons.Hud,
                    active = hudVisible,
                ) { setHudVisible(!hudVisible) },
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
                            when (slotAction) {
                                SlotAction.SAVE -> {
                                    bridge.saveToSlot(slot.id)
                                    toast(getString(R.string.retro_engine_saved_to, slot.name))
                                    queueCloudBackupAfterSave()
                                    pollFaster()
                                    menu.close()
                                }
                                // A registered slot that has never been written
                                // to has no save to read. Saying so beats
                                // closing the menu onto an unchanged game and
                                // leaving the player to wonder whether Load
                                // works at all.
                                SlotAction.LOAD ->
                                    if (!slot.exists) {
                                        toast(getString(R.string.retro_engine_slot_empty))
                                    } else {
                                        bridge.loadSlot(slot.id)
                                        toast(getString(R.string.retro_engine_loaded_from, slot.name))
                                        pollFaster()
                                        menu.close()
                                    }
                            }
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
            // Only offered when saving: creating a slot to load from would
            // hand the player an empty one.
            if (slotAction == SlotAction.SAVE) {
                add(
                    RetroMenuEntry.Action(getString(R.string.retro_engine_new_slot), RetroDrawerIcons.Add) {
                        bridge.newSlot()
                        queueCloudBackupAfterSave()
                        pollFaster()
                        menu.close()
                    },
                )
            }
        }

    /**
     * WinNative's own control settings, which belong to the pad rather than to
     * the engine -- the engine never sees the pad, only the keys it sends.
     *
     * The same builder the libretro path uses, so the player gets the identical
     * pane: the on-screen controls switch, the layout editor, opacity, colours
     * and haptics. Stick inversion is off because a Game Boy has no sticks.
     */
    private fun buildControlEntries(): List<RetroMenuEntry> =
        RetroControlsMenu.build(
            RetroControlsMenu.Host(
                context = this,
                overlay = pad,
                menu = menu,
                systemId = RetroSystems.GAMEBOY.id,
                touchControls = { touchControls },
                onTouchControls = { value ->
                    touchControls = value
                    applyTouchControls()
                    persistExtra(RetroShortcuts.KEY_TOUCH_CONTROLS, if (value) "1" else "0")
                },
                // No sticks on this system, so the adaptive-stick setting has
                // nothing to act on; reported as off and ignored.
                adaptiveSticks = { false },
                onAdaptiveSticks = { },
                orientationLabel = {
                    val host = mLayout
                    if ((host?.height ?: 0) > (host?.width ?: 0)) {
                        getString(R.string.retro_lr_portrait)
                    } else {
                        getString(R.string.retro_lr_landscape)
                    }
                },
                onCloseMenu = { menu.close() },
                showStickInversion = false,
            ),
        )

    // ------------------------------------------------------------------- HUD
    //
    // The performance overlay is WinNative's own view and measures the frames
    // it is drawn on, so it works over the engine exactly as it does over a
    // libretro core. Its settings are the global ones, shared with every other
    // system rather than kept separately for this path.

    private var hudVisible = false
    private var hudStyle = HudStyle()
    private var hudElements = RetroHudSupport.defaultElements()
    private var frameRating: FrameRating? = null

    private fun buildHudEntries(): List<RetroMenuEntry> =
        RetroHudSupport.buildHudEntries(
            context = this,
            hudVisible = hudVisible,
            style = hudStyle,
            elements = hudElements,
            onMaster = { setHudVisible(it) },
            onStyle = { next ->
                hudStyle = next
                frameRating?.let { RetroHudSupport.applyStyle(it, next, hudElements) }
                RetroHudSupport.saveGlobalHudStyle(this, next)
            },
            onElements = { next ->
                hudElements = next
                frameRating?.let { RetroHudSupport.applyStyle(it, hudStyle, next) }
                RetroHudSupport.saveGlobalHudElements(this, next)
            },
            onRebuild = { menu.rebuild() },
        )

    private fun setHudVisible(value: Boolean) {
        hudVisible = value
        if (value) {
            showHud()
        } else {
            frameRating?.visibility = android.view.View.GONE
            handler.removeCallbacks(hudTick)
        }
        persistExtra(RetroShortcuts.KEY_HUD, if (value) "1" else "0")
        menu.rebuild()
    }

    /**
     * Feeds the overlay one tick per engine frame.
     *
     * The overlay works out its frame rate from how often it is told a frame
     * happened, and nothing on this side ever sees one -- SDL presents on its
     * own thread, so there is no callback to hang this on and the overlay would
     * sit at zero. The engine reports its own rate instead, and this reproduces
     * it at the right cadence. It stops while paused, so the overlay reads zero
     * exactly when the game is not running.
     */
    private val hudTick =
        object : Runnable {
            override fun run() {
                val rating = frameRating ?: return
                val fps = bridge.state.fps
                if (!hudVisible || bridge.state.paused || fps <= 0) {
                    handler.postDelayed(this, HUD_IDLE_TICK_MS)
                    return
                }
                rating.recordGameFrame()
                handler.postDelayed(this, (1000L / fps).coerceAtLeast(1L))
            }
        }

    private fun showHud() {
        val host = mLayout ?: return
        var rating = frameRating
        if (rating == null) {
            rating = RetroHudSupport.createFrameRating(this, ENGINE_RENDERER_LABEL)
            frameRating = rating
            // Below the drawer so the menu is never drawn underneath the
            // overlay, which is the order the libretro path uses too.
            host.addView(rating, host.indexOfChild(menuView).coerceAtLeast(0))
            RetroHudSupport.applyStyle(rating, hudStyle, hudElements)
        }
        rating.visibility = android.view.View.VISIBLE
        rating.reset()
        handler.removeCallbacks(hudTick)
        handler.post(hudTick)
    }

    private fun buildBottomEntries(): List<RetroMenuEntry.Action> =
        buildList {
            // Pause stops the engine's game loop but not its command polling,
            // so Resume can reach it. The drawer stays open on Pause and closes
            // on Resume, which is how the libretro path behaves.
            if (bridge.state.paused) {
                add(
                    RetroMenuEntry.Action(
                        getString(R.string.retro_lr_resume),
                        RetroDrawerIcons.Resume,
                        active = true,
                    ) {
                        bridge.setPaused(false)
                        pollFaster()
                        menu.close()
                    },
                )
            } else {
                add(
                    RetroMenuEntry.Action(getString(R.string.retro_lr_pause), RetroDrawerIcons.Pause) {
                        bridge.setPaused(true)
                        pollFaster()
                    },
                )
            }
            add(
                RetroMenuEntry.Action(getString(R.string.retro_lr_exit), RetroDrawerIcons.Exit, danger = true) {
                // Save before leaving, the way closing a game on the libretro
                // path writes its state: the engine's save is the only record
                // of progress on this path.
                    bridge.saveGame()
                    queueCloudBackupAfterSave()
                    menu.close()
                    handler.postDelayed({ finish() }, EXIT_SAVE_GRACE_MS)
                },
            )
        }

    /**
     * Shows or hides the pad and re-lays the picture around it.
     *
     * Both halves matter: leaving the surface at its old size would waste the
     * space the pad just gave up, and hiding the pad without resizing would
     * leave the game in a letterbox for no reason.
     */
    private fun applyTouchControls() {
        pad?.visibility = if (touchControls) android.view.View.VISIBLE else android.view.View.GONE
        val host = mLayout ?: return
        pad?.let { view -> host.post { updateGameArea(host, view) } }
    }

    /**
     * Writes a setting back to the game's shortcut, so it is remembered per
     * game and agrees with what the settings screen shows -- the same place the
     * libretro path keeps these.
     */
    private fun persistExtra(key: String, value: String) {
        val path = intent.getStringExtra(EXTRA_SHORTCUT_PATH) ?: return
        Thread {
            runCatching {
                val shortcut =
                    persistShortcut ?: run {
                        val file = File(path)
                        if (!file.isFile) return@runCatching
                        val cm = ContainerManager(this)
                        Shortcut(cm.retroContainer, file)
                            .also { persistShortcut = it }
                    }
                shortcut.putExtra(key, value)
                shortcut.saveData()
            }.onFailure { Log.w(TAG, "could not persist $key: ${it.message}") }
        }.start()
    }

    /**
     * Reads back the per-game settings this activity owns. Falls back to the
     * system default for anything the shortcut has never had set, which is what
     * the libretro path does for a freshly added game.
     */
    private fun loadPersistedSettings() {
        val path = intent.getStringExtra(EXTRA_SHORTCUT_PATH)
        val shortcut =
            runCatching {
                path?.let { File(it) }?.takeIf { it.isFile }?.let { file ->
                    Shortcut(
                        ContainerManager(this).retroContainer,
                        file,
                    )
                }
            }.getOrNull()
        persistShortcut = shortcut

        touchControls =
            shortcut?.getExtra(RetroShortcuts.KEY_TOUCH_CONTROLS)?.takeIf { it.isNotEmpty() }?.let { it != "0" }
                ?: RetroDefaults.touchControls(this, RetroSystems.GAMEBOY.id)
        hudVisible = shortcut?.getExtra(RetroShortcuts.KEY_HUD)?.takeIf { it.isNotEmpty() }?.let { it != "0" } ?: false
        hudStyle = RetroHudSupport.loadGlobalHudStyle(this)
        hudElements = RetroHudSupport.loadGlobalHudElements(this)
    }

    /**
     * Queues this game's engine saves for cloud backup.
     *
     * Called after a save the menu asked for, and again when the activity goes
     * away -- that second one is what covers a save made from inside the game,
     * which the host never sees happen. The queue is the same one the libretro
     * path uses, so the upload runs the next time the app is in the foreground
     * with Drive connected; nothing here waits on the network.
     *
     * Off the main thread because staging copies files.
     */
    private fun queueCloudBackup() {
        val shortcut = persistShortcut ?: return
        if (shortcut.getExtra("cloud_sync_enabled", "1") == "0") return
        val gameName = intent.getStringExtra(EXTRA_GAME_NAME).orEmpty()
            .ifBlank { shortcut.getExtra("custom_name", shortcut.name) }
        val app = applicationContext
        Thread {
            runCatching { Gen1CloudSync.queueBackup(app, Gen1CloudSync.cloudId(shortcut), gameName) }
                .onFailure { Log.w(TAG, "could not queue cloud backup: ${it.message}") }
        }.apply { name = "gen1-cloud-queue"; start() }
    }

    /**
     * Gives the engine time to write the save it was just asked for, then
     * queues it. The command is only queued on the bridge when this returns,
     * and the engine polls for it -- so staging immediately would copy the
     * previous save.
     */
    private fun queueCloudBackupAfterSave() {
        handler.postDelayed({ queueCloudBackup() }, SAVE_SETTLE_MS)
    }

    private fun toast(text: String) {
        android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()
    }

    // --------------------------------------------------------------- polling

    /**
     * What to do when the engine's published state changes.
     *
     * The reading and parsing happen on the bridge's own thread; this is only
     * the part that touches the UI, so it is the only part on the main thread.
     * It is not called every tick -- the bridge stays quiet unless something
     * drawn here actually moved.
     */
    private fun onEngineState(state: Gen1EngineBridge.State, menuChanged: Boolean) {
        importState = state.import
        // Down as soon as the engine says a game is running. Also driven by the
        // import line disappearing, so a run that never needed an import --
        // every launch after the first -- does not sit behind the screen
        // waiting for progress that never comes.
        if (loadingVisible && state.booted) loadingVisible = false
        if (menuChanged && menu.visible) menu.rebuild()
    }

    /**
     * Asks for a read now, because something just changed the engine's state
     * and the menu is showing it.
     *
     * The command that caused the change is still queued on the bridge's
     * thread, and this lands behind it -- so the read always sees the engine
     * after the command reached it, not before. The cadence is left alone; that
     * belongs to whether the drawer is open, not to a single tap.
     */
    private fun pollFaster() = bridge.pollNow()

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

        // Started first and joined last, so this storage work overlaps the
        // engine coming up in super.onCreate rather than adding to it. The
        // values it produces are not needed until the very end of onCreate.
        val prefetch = startSettingsPrefetch()

        // This one has to be synchronous, and before super.onCreate: that call
        // starts the engine, and the engine enumerates its mods once while it
        // loads. Installing afterwards would not appear until the next launch.
        // The common path is a stat and a short stamp read; the unpack only
        // happens on the first launch after the bundle ships a new mod.
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

        val menuComposeView =
            ComposeView(this).apply {
                // Above both SDL's surface and the pad, so the drawer is not
                // drawn underneath the buttons it is replacing.
                elevation = MENU_ELEVATION
                setViewTreeLifecycleOwner(this@Gen1EngineActivity)
                setViewTreeViewModelStoreOwner(this@Gen1EngineActivity)
                setViewTreeSavedStateRegistryOwner(this@Gen1EngineActivity)
                setContent {
                    WinNativeTheme {
                        // The drawer can close itself -- B and Back are handled
                        // inside the controller -- so the cadence is driven off
                        // the state that actually decides it rather than from
                        // the call sites that happen to open it. Runs on the
                        // main thread and only when one of the two flips.
                        androidx.compose.runtime.LaunchedEffect(menu.visible, loadingVisible) {
                            bridge.setPollFast(menu.visible || loadingVisible)
                        }
                        Box(Modifier.fillMaxSize()) {
                            RetroDrawerMenu(menu)
                            // Above the drawer: during the import there is no
                            // game to configure, and the menu must not be
                            // reachable behind it.
                            Gen1LoadingScreen(
                                gameName = intent.getStringExtra(EXTRA_GAME_NAME).orEmpty(),
                                artwork = artwork,
                                state = importState,
                                visible = loadingVisible,
                            )
                        }
                    }
                }
            }
        menuView = menuComposeView
        host.addView(
            menuComposeView,
            android.widget.RelativeLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        // Joined here, not earlier: these are the first two lines that read
        // what it fetched, and by now it has had the whole of super.onCreate to
        // finish. Nothing has been laid out against a default yet, so there is
        // no flicker to correct.
        prefetch.join()

        // Applied after both views exist: it sizes the picture around the pad,
        // and shows the overlay if this game had it on last time.
        applyTouchControls()
        if (hudVisible) host.post { showHud() }
    }

    /**
     * Reads this activity's persisted settings and the shortcut's artwork off
     * the main thread.
     *
     * All of it is storage work: a Shortcut is a file, the defaults behind it
     * are SharedPreferences, and ContainerManager -- which a Shortcut needs to
     * be constructed -- lists the container directory and parses a JSON config
     * for each one it finds. That is a variable amount of disk and parsing on
     * the thread that is also trying to start a game.
     *
     * Returned rather than awaited, so the caller decides how much of its own
     * work to overlap with it.
     */
    private fun startSettingsPrefetch(): Thread =
        Thread {
            runCatching { loadPersistedSettings() }
                .onFailure { Log.w(TAG, "could not read saved settings: ${it.message}") }

            // Warms the preference file so the pad's own reads, which have to
            // happen on the main thread because they configure a view, come
            // out of the in-memory cache instead of parsing XML there.
            runCatching {
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            }

            // Decoded here rather than in the composable, and downsampled --
            // it is drawn at a couple of hundred dp, so decoding a full-size
            // cover at 1:1 would cost far more memory and time than the picture
            // is worth. A missing or unreadable file simply means no picture on
            // the loading screen, not a failure to start the game.
            val decoded =
                intent.getStringExtra(EXTRA_ARTWORK_PATH)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { path -> decodeArtwork(path) }
            // Compose state, so the loading screen picks the picture up whenever
            // it lands -- it is on screen before this finishes, and showing the
            // title without the cover for a moment is the right trade.
            if (decoded != null) handler.post { artwork = decoded }
        }.apply {
            name = "gen1-settings"
            priority = Thread.NORM_PRIORITY - 1
            start()
        }

    /** Decodes the shortcut's cover no larger than the loading screen draws it. */
    private fun decodeArtwork(path: String): android.graphics.Bitmap? =
        runCatching {
            val bounds =
                android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(path, bounds)
            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            if (longest <= 0) return@runCatching null
            val target = (ARTWORK_MAX_DP * resources.displayMetrics.density).toInt().coerceAtLeast(1)
            var sample = 1
            while (longest / (sample * 2) >= target) sample *= 2
            android.graphics.BitmapFactory.decodeFile(
                path,
                android.graphics.BitmapFactory.Options().apply { inSampleSize = sample },
            )
        }.getOrNull()

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
        // With the pad hidden there is nothing to leave room for, so the
        // picture takes the whole screen -- which is the reason to turn the pad
        // off in the first place.
        val budgetHeight = if (portrait && touchControls) (h * PORTRAIT_GAME_HEIGHT_FRACTION).toInt() else h
        val scale = minOf(w / GB_WIDTH, budgetHeight / GB_HEIGHT).coerceAtLeast(1)
        val gameWidth = (GB_WIDTH * scale).toFloat()
        val gameHeight = (GB_HEIGHT * scale).toFloat()

        // Centred across, and in portrait pushed to the top so every pixel the
        // picture does not use goes to the buttons underneath it.
        val left = (w - gameWidth) * 0.5f
        val top = if (portrait && touchControls) 0f else (h - gameHeight) * 0.5f
        val area = android.graphics.RectF(left, top, left + gameWidth, top + gameHeight)

        view.setGameArea(area)
        applySurfaceBounds(area)
        applyFillScale(area, w, h)
    }

    /**
     * Stretches the picture over the whole display when the pad is hidden.
     *
     * With the buttons gone there is nothing to leave room for, so the black
     * bars either side serve no purpose -- the screen should be the game. The
     * surface keeps its exact 160x144-multiple size, which is what keeps the
     * pixels sharp, and the view is scaled up from there to cover the display.
     *
     * This does not preserve the Game Boy's 10:9 aspect: filling a 16:9 screen
     * means stretching horizontally, which is the trade the setting asks for.
     * With the pad shown the scale is reset to 1 and the aspect is exact.
     */
    private fun applyFillScale(area: android.graphics.RectF, hostWidth: Int, hostHeight: Int) {
        val surface = mSurface ?: return
        if (touchControls || area.width() <= 0f || area.height() <= 0f) {
            surface.scaleX = 1f
            surface.scaleY = 1f
            return
        }
        // Scaled about its own centre, and the rectangle is centred in the host
        // whenever the pad is hidden, so the result lands flush with the edges.
        surface.pivotX = area.width() * 0.5f
        surface.pivotY = area.height() * 0.5f
        surface.scaleX = hostWidth / area.width()
        surface.scaleY = hostHeight / area.height()
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
        const val EXTRA_ARTWORK_PATH = "wn.engine.artwork"

        private const val DPAD_DEADZONE = 0.35f

        private const val PREF_HAPTIC = "retro_haptic_strength"
        private const val DEFAULT_HAPTIC = 0.4f

        /**
         * Named on the HUD's renderer line. The engine draws through LOVE on
         * OpenGL ES, so this says what is actually presenting the frames rather
         * than borrowing the libretro label.
         */
        private const val ENGINE_RENDERER_LABEL = "LOVE / GLES"

        /** Above SDL's surface and the pad. */
        private const val MENU_ELEVATION = 2000f

        /** Between the parts of a save slot's progress line. */
        private const val SUBTITLE_SEPARATOR = "  \u00b7  "

        /** How often the HUD feeder rechecks when there is nothing to report. */
        private const val HUD_IDLE_TICK_MS = 250L

        /**
         * Longest edge the loading screen's cover is ever drawn at, which is
         * what it gets decoded to rather than at full size.
         */
        private const val ARTWORK_MAX_DP = 240f

        /** Long enough for the engine to pick the save command up and run it. */
        private const val EXIT_SAVE_GRACE_MS = 400L

        /**
         * How long to let the engine actually write a save before copying it
         * for the cloud. The command reaches the engine on its next poll, so
         * staging any sooner would copy the previous save.
         */
        private const val SAVE_SETTLE_MS = 700L

        // Engine option row ids, grouped the way WinNative presents settings.
        // Anything not listed lands on System; see paneForRow.
        /** The two Display rows that pair up as buttons instead of dropdowns. */
        private const val ANIMATIONS_ROW = "animations"
        private const val VIDEO_MODE_ROW = "videoMode"

        /**
         * Engine rows that decide nothing on this path, and so are not shown.
         *
         * An unknown row is deliberately kept and shown -- that is how an option
         * added upstream stays reachable without a change here. This is the
         * opposite case: a row that is present but inert, which is worse than an
         * absent one because it invites the player to change something that
         * cannot change.
         *
         * TOUCH PAD switches the engine's own on-screen D-pad, which this fork
         * builds with that overlay forced off (see WINNATIVE_TOUCH_ARG in
         * TouchControls.lua) because WinNative draws the pad itself. Toggling it
         * would write a value nothing reads, and the switch the player actually
         * wants is on the Controls pane.
         */
        private val HIDDEN_ROWS = setOf("touchControls")

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
