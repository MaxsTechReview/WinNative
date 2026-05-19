package com.winlator.cmod.feature.stores.steam.utils

import org.json.JSONObject

/**
 * In-house KeyValues node — the receiver type for [generateSteamApp] and the
 * other decoders in KeyValueUtils.kt. It replaces JavaSteam's `KeyValue` so
 * PICS appinfo fetched by the C++ wn-steam-client can be turned into a
 * [com.winlator.cmod.feature.stores.steam.data.SteamApp] without the
 * JavaSteam dependency (Phase 9).
 *
 * Backed by a nested `Map<String, Any>` where a value is either a leaf
 * (`String` / `Int` / `Long` / `Float`) or a nested `Map` (a subsection).
 * The C++ side parses the PICS appinfo VDF (text or binary — `vdf::parse_auto`)
 * and hands Kotlin a JSON object; [fromJsonObject] turns that into this IR.
 * A missing key yields the shared [INVALID] node, mirroring JavaSteam's
 * `KeyValue.Invalid` sentinel — so `kv["a"]["b"]["c"]` never NPEs and the
 * `asX()` getters fall back to their defaults.
 */
class WnKeyValue private constructor(
    val name: String?,
    private val leaf: Any?,
    private val map: Map<String, Any>?,
) {
    companion object {
        private val INVALID = WnKeyValue(null, null, null)

        /** Wrap an already-parsed KeyValues map as the (unnamed) root node. */
        fun fromMap(map: Map<String, Any>): WnKeyValue = WnKeyValue(null, null, map)

        /**
         * Build a node from a JSON object — the form the C++ wn-steam-client
         * emits for a parsed PICS appinfo VDF tree (nested objects for
         * subsections, string leaves for values).
         */
        fun fromJsonObject(obj: JSONObject): WnKeyValue = WnKeyValue(null, null, jsonObjectToMap(obj))

        private fun jsonObjectToMap(obj: JSONObject): Map<String, Any> {
            val m = LinkedHashMap<String, Any>()
            for (key in obj.keys()) {
                when (val v = obj.get(key)) {
                    is JSONObject -> m[key] = jsonObjectToMap(v)
                    else -> m[key] = v.toString()
                }
            }
            return m
        }
    }

    /** Sub-node lookup; returns [INVALID] (never null) when absent. */
    operator fun get(key: String): WnKeyValue {
        val m = map ?: return INVALID
        return when (val v = m[key]) {
            null -> INVALID
            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                WnKeyValue(key, null, v as Map<String, Any>)
            }
            else -> WnKeyValue(key, v, null)
        }
    }

    /** Child nodes, in insertion order; empty for a leaf / invalid node. */
    val children: List<WnKeyValue>
        get() {
            val m = map ?: return emptyList()
            return m.entries.map { (k, v) ->
                if (v is Map<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    WnKeyValue(k, null, v as Map<String, Any>)
                } else {
                    WnKeyValue(k, v, null)
                }
            }
        }

    /** Leaf value as a string (numbers stringified); null for a subsection. */
    val value: String?
        get() = when (leaf) {
            null -> null
            is String -> leaf
            else -> leaf.toString()
        }

    fun asInteger(default: Int = 0): Int = when (val l = leaf) {
        is Int -> l
        is Long -> l.toInt()
        is Float -> l.toInt()
        is String -> l.trim().toIntOrNull() ?: l.trim().toDoubleOrNull()?.toInt() ?: default
        else -> default
    }

    fun asLong(default: Long = 0L): Long = when (val l = leaf) {
        is Long -> l
        is Int -> l.toLong()
        is Float -> l.toLong()
        is String -> l.trim().toLongOrNull() ?: l.trim().toDoubleOrNull()?.toLong() ?: default
        else -> default
    }

    fun asBoolean(default: Boolean = false): Boolean = when (val l = leaf) {
        is Int -> l != 0
        is Long -> l != 0L
        is Float -> l != 0f
        is String -> {
            val s = l.trim()
            when {
                s.equals("true", ignoreCase = true) -> true
                s.equals("false", ignoreCase = true) -> false
                else -> (s.toIntOrNull() ?: 0) != 0
            }
        }
        else -> default
    }

    fun asByte(default: Byte = 0): Byte = asInteger(default.toInt()).toByte()
}
