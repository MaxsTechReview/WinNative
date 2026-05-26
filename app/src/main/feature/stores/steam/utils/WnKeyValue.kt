package com.winlator.cmod.feature.stores.steam.utils

import org.json.JSONObject

/**
 * Lightweight KeyValues node used by [generateSteamApp] and the other Steam appinfo decoders.
 *
 * This wraps the appinfo tree parsed by the C++ wn-steam-client without depending on
 * JavaSteam's `KeyValue`. Values are either scalar leaves or nested maps.
 *
 * Missing keys return the shared [INVALID] node, so chained lookups stay null-safe and
 * `asX()` getters fall back to their defaults.
 */
class WnKeyValue private constructor(
    val name: String?,
    private val leaf: Any?,
    private val map: Map<String, Any>?,
) {
    companion object {
        private val INVALID = WnKeyValue(null, null, null)

        /** Wraps an already-parsed KeyValues map as an unnamed root node. */
        fun fromMap(map: Map<String, Any>): WnKeyValue = WnKeyValue(null, null, map)

        /** Builds a node from the JSON tree emitted by the C++ appinfo parser. */
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

    /** Looks up a child node, returning [INVALID] when absent. */
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

    /** Child nodes in insertion order, or empty for leaf and invalid nodes. */
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

    /** Leaf value as a string, or null for subsection and invalid nodes. */
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
