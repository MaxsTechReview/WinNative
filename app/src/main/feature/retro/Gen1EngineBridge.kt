package com.winlator.cmod.feature.retro

import android.content.Context
import android.util.Log
import java.io.File

class Gen1EngineBridge(context: Context) {
    private val dir = File(context.getExternalFilesDir(null), "$SAVE_SUBDIR/$BRIDGE_SUBDIR")
    private val statePath = File(dir, "state.txt")
    private val commandPath = File(dir, "cmd.txt")
    private val commandTmp = File(dir, "cmd.host.tmp")

    private val ioThread =
        android.os.HandlerThread(THREAD_NAME, android.os.Process.THREAD_PRIORITY_BACKGROUND)
            .apply { start() }
    private val io = android.os.Handler(ioThread.looper)
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    private var listener: ((State, Boolean) -> Unit)? = null

    @Volatile
    private var polling = false

    @Volatile
    private var pollFast = false

    data class Row(
        val id: String,
        val label: String,
        val value: String,
        val steppable: Boolean,
        val values: List<String> = emptyList(),
        val selectedIndex: Int = -1,
    )

    data class Slot(
        val id: String,
        val name: String,
        val playTime: String,
        val badges: Int,
        val caught: Int,
        val active: Boolean,
        val exists: Boolean,
    )

    data class Import(val stage: String, val progress: Float)

    data class State(
        val seq: Long,
        val booted: Boolean,
        val paused: Boolean,
        val fastForward: Boolean,
        val fps: Int,
        val import: Import?,
        val version: String,
        val rows: List<Row>,
        val slots: List<Slot>,
    )

    @Volatile
    var state: State = EMPTY
        private set

    private val pollTask =
        object : Runnable {
            override fun run() {
                if (!polling) return
                val before = state
                val moved = refresh()
                val now = state
                if (moved || now.import != before.import || now.booted != before.booted) {
                    val callback = listener
                    if (callback != null) main.post { callback(now, moved) }
                }
                io.postDelayed(this, if (pollFast) POLL_ACTIVE_MS else POLL_IDLE_MS)
            }
        }

    fun startPolling(onChanged: (State, Boolean) -> Unit) {
        listener = onChanged
        polling = true
        io.removeCallbacks(pollTask)
        io.post(pollTask)
    }

    fun stopPolling() {
        polling = false
        io.removeCallbacks(pollTask)
    }

    fun setPollFast(fast: Boolean) {
        if (pollFast == fast) return
        pollFast = fast
        if (polling) pollNow()
    }

    fun pollNow() {
        if (!polling) return
        io.removeCallbacks(pollTask)
        io.post(pollTask)
    }

    fun shutdown() {
        polling = false
        listener = null
        io.removeCallbacksAndMessages(null)
        ioThread.quitSafely()
    }

    private fun refresh(): Boolean {
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
        val ladders = HashMap<String, Pair<Int, List<String>>>()

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
                                progress = ((f[2].toIntOrNull() ?: 0) / 1000f).coerceIn(0f, 1f),
                            )
                    }
                "version" -> version = f.getOrNull(1).orEmpty()
                "row" ->
                    if (f.size >= 5) {
                        rows.add(Row(f[1], f[2], f[3], f[4] == "step"))
                    }
                "vals" ->
                    if (f.size >= 4) {
                        ladders[f[1]] = (f[2].toIntOrNull() ?: 0) to f.subList(3, f.size).toList()
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
        val withLadders =
            rows.map { row ->
                val (index, values) = ladders[row.id] ?: return@map row
                row.copy(values = values, selectedIndex = index.coerceIn(0, values.size - 1))
            }
        return State(seq, booted, paused, fastForward, fps, import, version, withLadders, slots)
    }

    fun row(id: String): Row? = state.rows.firstOrNull { it.id == id }

    fun send(vararg commands: String) {
        if (commands.isEmpty()) return
        io.post { writeCommands(commands) }
    }

    private fun writeCommands(commands: Array<out String>) {
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
                commandPath.writeText(body)
                commandTmp.delete()
            }
        }.onFailure { Log.w(TAG, "could not queue engine command: ${it.message}") }
    }

    fun step(
        id: String,
        direction: Int,
    ) = send("step\t$id\t${if (direction < 0) -1 else 1}")

    fun setRow(
        id: String,
        index: Int,
    ) = send("set\t$id\t$index")

    fun activate(id: String) = send("activate\t$id")

    fun saveGame() = send("save")

    fun loadSlot(id: String) = send("loadslot\t$id")

    fun saveToSlot(id: String) = send("saveslot\t$id")

    fun newSlot() = send("newslot")

    fun renameSlot(
        id: String,
        name: String,
    ) = send("renameslot\t$id\t${name.replace('\t', ' ').replace('\n', ' ').trim()}")

    fun reset() = send("reset")

    fun setPaused(paused: Boolean) = send("pause\t${if (paused) 1 else 0}")

    fun setFastForward(on: Boolean) = send("ff\t${if (on) 1 else 0}")

    fun clearStale() {
        io.post {
            runCatching {
                statePath.delete()
                commandPath.delete()
                commandTmp.delete()
            }
        }
    }

    companion object {
        private const val TAG = "WnGen1Bridge"
        private const val THREAD_NAME = "gen1-bridge-io"

        private const val POLL_ACTIVE_MS = 200L
        private const val POLL_IDLE_MS = 700L

        private const val SAVE_SUBDIR = "save/pokemon-love2d"
        private const val BRIDGE_SUBDIR = "winnative"

        val EMPTY =
            State(
                seq = -1L, booted = false, paused = false, fastForward = false, fps = 0,
                import = null, version = "", rows = emptyList(), slots = emptyList(),
            )

        private val MOD_ROW_PREFIXES = listOf("pipeline:", "$VOXEL_MOD_ROW_OWNER:")

        fun isModRow(id: String): Boolean = MOD_ROW_PREFIXES.any { id.startsWith(it) }

        private const val VOXEL_MOD_ROW_OWNER = "DRAMATIC_SHAPE"
    }
}
