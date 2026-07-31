package com.winlator.cmod.feature.retro

import android.content.Context
import android.util.Log
import java.io.File

/**
 * The host half of the engine control channel.
 *
 * The engine publishes its own OPTIONS rows and save slots as text, and takes
 * commands the same way; see src/core/WinNativeBridge.lua in the gen1recomp
 * fork for the other end and for why it works this way.
 *
 * This exists because the Retro menu has to show real values. WinNative owns
 * the menu but not the settings, so a row that says "VOXEL: ON" has to have
 * read that from the engine -- and the engine is a separate runtime with its
 * own state, not a libretro core whose variables the host already holds. Key
 * injection, which is what this replaces, could only ever ask for a change and
 * never observe one.
 *
 * Both files live in the engine's LOVE save directory, which sits under
 * WinNative's own external files directory, so no permission is involved.
 */
class Gen1EngineBridge(context: Context) {
    private val dir = File(context.getExternalFilesDir(null), "$SAVE_SUBDIR/$BRIDGE_SUBDIR")
    private val statePath = File(dir, "state.txt")
    private val commandPath = File(dir, "cmd.txt")
    private val commandTmp = File(dir, "cmd.host.tmp")

    /** One engine option, exactly as its own OPTIONS menu would show it. */
    data class Row(
        val id: String,
        val label: String,
        val value: String,
        /**
         * Whether the row cycles through values or just does something. The
         * engine reports this rather than the host guessing, because a mod can
         * add rows of either kind.
         */
        val steppable: Boolean,
    )

    data class Slot(
        val id: String,
        /** The slot's custom label, or the player character's name if unlabelled. */
        val name: String,
        /** Play time as the engine formats it, e.g. "3:14". Empty for an unused slot. */
        val playTime: String,
        val badges: Int,
        /** How many Pokedex entries have been caught. */
        val caught: Int,
        val active: Boolean,
        /** False for a registered slot that has never been written to. */
        val exists: Boolean,
    )

    /** Where a first-boot ROM import has got to. */
    data class Import(val stage: String, val progress: Float)

    data class State(
        val seq: Long,
        /**
         * False while the engine is still importing the ROM. The menu shows
         * this rather than an empty settings list, which would look broken.
         */
        val booted: Boolean,
        /** The host asked the engine to hold still; its update loop is stopped. */
        val paused: Boolean,
        val fastForward: Boolean,
        /** The engine's own frame rate; nothing on this side can observe it. */
        val fps: Int,
        /**
         * Set only while a first-boot ROM import is running. Null the rest of
         * the time, which is how the loading screen knows to come down.
         */
        val import: Import?,
        val version: String,
        val rows: List<Row>,
        val slots: List<Slot>,
    )

    /**
     * The last state read. Kept so the menu can be built synchronously from
     * whatever is current, while polling refreshes it in the background.
     */
    @Volatile
    var state: State = EMPTY
        private set

    /**
     * Reads the published state, and returns true when the sequence number
     * moved -- the caller's signal to rebuild the menu.
     *
     * The state is taken every time regardless, because not everything in it
     * belongs on the menu: the frame rate changes on every poll and the engine
     * deliberately does not advance the sequence for it, so it has to be picked
     * up without triggering a rebuild.
     */
    fun refresh(): Boolean {
        val text =
            runCatching { if (statePath.isFile) statePath.readText() else null }
                .getOrNull() ?: return false
        val parsed = runCatching { parse(text) }.getOrNull() ?: return false
        val moved = parsed.seq != state.seq
        state = parsed
        return moved
    }

    private fun parse(text: String): State {
        var seq = 0L
        var booted = false
        var paused = false
        var fastForward = false
        var fps = 0
        var import: Import? = null
        var version = ""
        val rows = ArrayList<Row>()
        val slots = ArrayList<Slot>()

        for (line in text.lineSequence()) {
            if (line.isEmpty()) continue
            val f = line.split('\t')
            when (f.getOrNull(0)) {
                "seq" -> seq = f.getOrNull(1)?.toLongOrNull() ?: 0L
                "booted" -> booted = f.getOrNull(1) == "1"
                "paused" -> paused = f.getOrNull(1) == "1"
                "ff" -> fastForward = f.getOrNull(1) == "1"
                "fps" -> fps = f.getOrNull(1)?.toIntOrNull() ?: 0
                "import" ->
                    if (f.size >= 3) {
                        import =
                            Import(
                                stage = f[1],
                                // Sent in thousandths: the wire format is text,
                                // and an integer cannot drift the way a
                                // formatted float can across locales.
                                progress = ((f[2].toIntOrNull() ?: 0) / 1000f).coerceIn(0f, 1f),
                            )
                    }
                "version" -> version = f.getOrNull(1).orEmpty()
                "row" ->
                    if (f.size >= 5) {
                        rows.add(Row(f[1], f[2], f[3], f[4] == "step"))
                    }
                "save" ->
                    if (f.size >= 8) {
                        slots.add(
                            Slot(
                                id = f[1],
                                name = f[2],
                                playTime = f[3],
                                badges = f[4].toIntOrNull() ?: 0,
                                caught = f[5].toIntOrNull() ?: 0,
                                active = f[6] == "1",
                                exists = f[7] == "1",
                            ),
                        )
                    }
            }
        }
        return State(seq, booted, paused, fastForward, fps, import, version, rows, slots)
    }

    fun row(id: String): Row? = state.rows.firstOrNull { it.id == id }

    /**
     * Queues commands for the engine.
     *
     * Appends to any batch the engine has not consumed yet rather than
     * replacing it: the two processes share no lock, so a player who taps two
     * rows quickly must not lose the first. Written through a temporary file
     * and renamed so the engine can never read a half-written batch.
     */
    @Synchronized
    fun send(vararg commands: String) {
        if (commands.isEmpty()) return
        runCatching {
            dir.mkdirs()
            val pending = if (commandPath.isFile) commandPath.readText() else ""
            val body = buildString {
                append(pending)
                if (pending.isNotEmpty() && !pending.endsWith("\n")) append('\n')
                for (c in commands) {
                    append(c)
                    append('\n')
                }
            }
            commandTmp.writeText(body)
            if (!commandTmp.renameTo(commandPath)) {
                // Same directory, so a rename should never fail; falling back
                // keeps a menu tap working if it somehow does.
                commandPath.writeText(body)
                commandTmp.delete()
            }
        }.onFailure { Log.w(TAG, "could not queue engine command: ${it.message}") }
    }

    fun step(
        id: String,
        direction: Int,
    ) = send("step\t$id\t${if (direction < 0) -1 else 1}")

    fun activate(id: String) = send("activate\t$id")

    fun saveGame() = send("save")

    fun loadSlot(id: String) = send("loadslot\t$id")

    /** Writes the game into a chosen slot, making it the active one. */
    fun saveToSlot(id: String) = send("saveslot\t$id")

    fun newSlot() = send("newslot")

    /**
     * Tabs and newlines separate fields on the wire, so a name containing one
     * would be read back as extra fields. Spaces are fine and common.
     */
    fun renameSlot(
        id: String,
        name: String,
    ) = send("renameslot\t$id\t${name.replace('\t', ' ').replace('\n', ' ').trim()}")

    fun reset() = send("reset")

    /**
     * Holds the game still. Only the engine's game loop stops -- it keeps
     * reading commands, or nothing could unpause it, and it keeps drawing, so
     * the paused frame stays on screen.
     */
    fun setPaused(paused: Boolean) = send("pause\t${if (paused) 1 else 0}")

    /** Drives the engine's own speed setting, and restores it when turned off. */
    fun setFastForward(on: Boolean) = send("ff\t${if (on) 1 else 0}")

    /**
     * Clears state left by a previous run, so the menu cannot show the last
     * session's values for the few hundred milliseconds before the engine
     * publishes its first state. Called once at launch.
     */
    fun clearStale() {
        runCatching {
            statePath.delete()
            commandPath.delete()
            commandTmp.delete()
        }
    }

    companion object {
        private const val TAG = "WnGen1Bridge"

        /**
         * LOVE derives this from the game's identity, so it is fixed by the
         * engine rather than chosen here.
         */
        private const val SAVE_SUBDIR = "save/pokemon-love2d"
        private const val BRIDGE_SUBDIR = "winnative"

        val EMPTY =
            State(
                seq = -1L, booted = false, paused = false, fastForward = false, fps = 0,
                import = null, version = "", rows = emptyList(), slots = emptyList(),
            )

        /**
         * Row ids belonging to the 3D mod rather than the engine, matched by
         * prefix so a setting the mod adds later needs no change here. The mod
         * registers two render pipelines ("pipeline:voxel",
         * "pipeline:tiltshift") and its own settings under its manifest id.
         *
         * This only decides the ORDER rows appear in -- 3D first on the
         * Display pane, since that is what the player turned this mode on for.
         * Values and labels always come from the engine, and a row that is not
         * there (mod disabled, or absent from the bundle) simply does not show.
         */
        private val MOD_ROW_PREFIXES = listOf("pipeline:", "$VOXEL_MOD_ROW_OWNER:")

        fun isModRow(id: String): Boolean = MOD_ROW_PREFIXES.any { id.startsWith(it) }

        /** Matches the mod's manifest id; see voxelmod/manifest.json. */
        private const val VOXEL_MOD_ROW_OWNER = "DRAMATIC_SHAPE"
    }
}
