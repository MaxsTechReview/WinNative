package com.winlator.cmod.runtime.display

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

// App-wide owner of the single Viture USB controller plus the persisted glasses settings, shared by
// the library (pre-container control) and the in-game external-display swap so only one component
// ever claims the USB interface. Settings set anywhere persist and re-apply on every (re)connect.
object GlassesManager {

    data class Settings(
        val refreshHz: Int = 120,
        val brightness: Int = -1, // -1 = full (100%) until the user picks a level
        val volume: Int = -1,     // -1 = full (100%) until the user picks a level
        val sunblock: Boolean = true, // glasses ship with the film on; match that by default
        val threeD: Boolean = false,
        val renderHeight: Int = 0, // 0 = native panel resolution; otherwise a render-scaling height
    )

    fun interface Listener { fun onGlassesChanged(connectionChanged: Boolean) }

    private var viture: VitureGlasses? = null
    private var prefs: SharedPreferences? = null
    private val listeners = CopyOnWriteArrayList<Listener>()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()
    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (viture != null) return
        val app = context.applicationContext
        prefs = app.getSharedPreferences("viture_glasses", Context.MODE_PRIVATE)
        _settings.value = load()
        viture = VitureGlasses(app).also { v ->
            v.setConnectionListener { c -> onConnectionChanged(c) }
            v.attach()
        }
    }

    fun glasses(): VitureGlasses? = viture
    fun isConnected(): Boolean = viture?.isConnected() == true
    fun modelName(): String = viture?.modelName() ?: "Viture"
    fun supportsBrightness(): Boolean = viture?.supportsBrightness() == true
    fun supportsVolume(): Boolean = viture?.supportsVolume() == true
    fun supportsFilm(): Boolean = viture?.supportsFilm() == true
    fun supports3D(): Boolean = viture?.supports3D() == true
    fun brightnessMax(): Int = viture?.brightnessMax() ?: 8
    fun volumeMax(): Int = viture?.volumeMax() ?: 8

    fun currentRefreshHz(): Int = _settings.value.refreshHz
    fun currentBrightness(): Int = _settings.value.brightness.let { if (it >= 0) it else brightnessMax() }
    fun currentVolume(): Int = _settings.value.volume.let { if (it >= 0) it else volumeMax() }
    fun isSunblock(): Boolean = _settings.value.sunblock
    fun is3D(): Boolean = _settings.value.threeD
    fun currentRenderHeight(): Int = _settings.value.renderHeight

    fun addListener(l: Listener) { listeners.add(l) }
    fun removeListener(l: Listener) { listeners.remove(l) }

    private fun onConnectionChanged(c: Boolean) {
        _connected.value = c
        if (c) applyAll()
        notifyListeners(true)
    }

    private fun applyAll() {
        val s = _settings.value
        viture?.let { v ->
            v.forceRefreshHz(s.refreshHz)
            v.setBrightness(if (s.brightness < 0) v.brightnessMax() else s.brightness)
            v.setVolume(if (s.volume < 0) v.volumeMax() else s.volume)
            v.setFilm(if (s.sunblock) 1 else 0)
            if (s.threeD) v.set3D(true)
        }
    }

    fun setRefreshHz(hz: Int) { update { it.copy(refreshHz = hz) }; viture?.forceRefreshHz(hz) }
    fun persistRefreshHz(hz: Int) { update { it.copy(refreshHz = hz) } } // caller already applied the mode
    fun setBrightness(value: Int) { update { it.copy(brightness = value) }; viture?.setBrightness(value) }
    fun setVolume(value: Int) { update { it.copy(volume = value) }; viture?.setVolume(value) }
    fun setSunblock(on: Boolean) { update { it.copy(sunblock = on) }; viture?.setFilm(if (on) 1 else 0) }
    fun set3D(on: Boolean) {
        update { it.copy(threeD = on) }
        if (on) viture?.set3D(true) else viture?.forceRefreshHz(currentRefreshHz())
    }
    fun setRenderHeight(height: Int) { update { it.copy(renderHeight = height) } }

    private fun update(transform: (Settings) -> Settings) {
        val next = transform(_settings.value)
        _settings.value = next
        save(next)
        notifyListeners(false)
    }

    private fun notifyListeners(connectionChanged: Boolean) {
        listeners.forEach { it.onGlassesChanged(connectionChanged) }
    }

    private fun load(): Settings {
        val p = prefs ?: return Settings()
        return Settings(
            p.getInt("refreshHz", 120),
            p.getInt("brightness", -1),
            p.getInt("volume", -1),
            p.getBoolean("sunblock", true),
            p.getBoolean("threeD", false),
            p.getInt("renderHeight", 0),
        )
    }

    private fun save(s: Settings) {
        prefs?.edit()?.apply {
            putInt("refreshHz", s.refreshHz)
            putInt("brightness", s.brightness)
            putInt("volume", s.volume)
            putBoolean("sunblock", s.sunblock)
            putBoolean("threeD", s.threeD)
            putInt("renderHeight", s.renderHeight)
        }?.apply()
    }
}
