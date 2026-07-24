package com.winlator.cmod.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.winlator.cmod.runtime.reshade.ReshadeLoadout
import com.winlator.cmod.runtime.reshade.ReshadeManager
import org.json.JSONObject

// serializes to the reshadeLoadout array + nested reshadeParams object; migrates legacy single-effect saves.
class ReshadeLoadoutState {
    var mode by mutableStateOf(ReshadeLoadout.MODE_SOLO)
        private set
    val order = mutableStateListOf<String>()
    private val enabledMap = mutableStateMapOf<String, Boolean>()
    private val paramValues = mutableStateMapOf<String, Float>()    // "<effect>::<uniformKey>"

    fun isEnabled(name: String): Boolean = enabledMap[name] ?: true

    fun setEnabled(name: String, on: Boolean) {
        if (mode == ReshadeLoadout.MODE_SOLO && on) order.forEach { enabledMap[it] = (it == name) }
        else enabledMap[name] = on
    }

    fun changeMode(m: String) {
        mode = ReshadeLoadout.normalizeMode(m)
        if (mode == ReshadeLoadout.MODE_SOLO) {
            var seen = false
            order.forEach { n ->
                val on = enabledMap[n] ?: false
                if (on && !seen) seen = true else enabledMap[n] = false
            }
        }
    }

    fun contains(name: String): Boolean = order.contains(name)

    fun isFull(): Boolean = order.size >= ReshadeLoadout.MAX_EFFECTS

    // capped at MAX_EFFECTS so a launch can't balloon into an unbounded compile.
    fun add(effect: ReshadeManager.ReshadeEffect, saved: JSONObject?) {
        if (order.contains(effect.name) || isFull()) return
        order.add(effect.name)
        seed(effect, saved)
        setEnabled(effect.name, true)
    }

    fun remove(name: String) {
        order.remove(name)
        enabledMap.remove(name)
        val prefix = "$name::"
        paramValues.keys.filter { it.startsWith(prefix) }.toList().forEach { paramValues.remove(it) }
        if (mode == ReshadeLoadout.MODE_SOLO && order.isNotEmpty() && order.none { isEnabled(it) })
            setEnabled(order.first(), true)
    }

    // chain order = apply order.
    fun move(from: Int, to: Int) {
        if (from !in order.indices || to !in order.indices || from == to) return
        val name = order.removeAt(from)
        order.add(to, name)
    }

    private fun seed(effect: ReshadeManager.ReshadeEffect, saved: JSONObject?) {
        val tmp = HashMap<String, Float>()
        for (p in effect.params) ReshadeManager.seedValues(p, saved, tmp)
        for ((k, v) in tmp) paramValues["${effect.name}::$k"] = v
    }

    fun paramValue(effect: String, key: String, fallback: Float): Float = paramValues["$effect::$key"] ?: fallback
    fun setParam(effect: String, key: String, value: Float) { paramValues["$effect::$key"] = value }

    fun loadoutJsonOrNull(): String? {
        if (order.isEmpty()) return null
        return ReshadeLoadout.serialize(order.map { ReshadeLoadout.Entry(it, isEnabled(it)) })
    }

    fun paramsJsonOrNull(): String? {
        if (order.isEmpty()) return null
        val root = JSONObject()
        for (name in order) {
            val eff = JSONObject()
            val prefix = "$name::"
            for ((k, v) in paramValues) if (k.startsWith(prefix)) eff.put(k.removePrefix(prefix), v.toDouble())
            if (eff.length() > 0) root.put(name, eff)
        }
        return if (root.length() == 0) null else root.toString()
    }

    fun firstEffectName(): String = order.firstOrNull() ?: "None"

    fun isEmpty(): Boolean = order.isEmpty()

    // effects no longer present in the drop-in folder are dropped; migrates a legacy single effect + flat params.
    fun init(
        effects: List<ReshadeManager.ReshadeEffect>,
        loadoutJson: String?,
        modeStr: String?,
        paramsJson: String?,
        legacyEffect: String?,
    ) {
        order.clear(); enabledMap.clear(); paramValues.clear()
        mode = ReshadeLoadout.normalizeMode(modeStr)
        val nested = !loadoutJson.isNullOrEmpty()
        for (e in ReshadeLoadout.parse(loadoutJson, legacyEffect)) {
            val effect = effects.firstOrNull { it.name.equals(e.name, true) } ?: continue
            if (order.size >= ReshadeLoadout.MAX_EFFECTS) break
            order.add(effect.name)
            enabledMap[effect.name] = e.enabled
            seed(effect, ReshadeLoadout.paramsForEffect(paramsJson, effect.name, nested, legacyEffect))
        }
        changeMode(mode) // enforce the solo invariant
    }

    // re-seed after a rescan, preserving current values; only drops effects whose folder vanished.
    fun reconcile(effects: List<ReshadeManager.ReshadeEffect>) {
        val present = effects.associateBy { it.name }
        order.filter { it !in present }.toList().forEach { remove(it) }
        for (name in order) {
            val effect = present[name] ?: continue
            val tmp = HashMap<String, Float>()
            for (p in effect.params) ReshadeManager.seedValues(p, null, tmp)
            for ((k, v) in tmp) {
                val full = "$name::$k"
                if (!paramValues.containsKey(full)) paramValues[full] = v
            }
        }
    }
}
