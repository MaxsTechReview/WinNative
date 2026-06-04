package com.winlator.cmod.feature.stores.steam.utils

import java.io.File

/**
 * Valve KeyValues node with text VDF parsing and serialization.
 *
 * Used for Steam config files such as `localconfig.vdf`, `appmanifest_*.acf`,
 * `config.vdf`, `local.vdf`, and Steam Input controller manifests. The API mirrors
 * the JavaSteam `KeyValue` surface used by the app.
 *
 * Only text VDF is supported because all current callers use text files.
 */
class KeyValue(
    var name: String? = null,
    var value: String? = null,
) {
    val children: MutableList<KeyValue> = mutableListOf()

    /** True for `{ }` sections rather than `"key" "value"` leaves. */
    var isSection: Boolean = false

    /**
     * Looks up a child by case-insensitive name.
     *
     * Missing children return [INVALID], so chained lookups stay null-safe.
     */
    operator fun get(key: String): KeyValue =
        children.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: INVALID

    /** The node value, or null for sections and missing nodes. */
    fun asString(): String? = value

    /**
     * Serializes this node as text VDF to [file].
     *
     * [asBinary] is accepted for call-site compatibility but ignored.
     */
    fun saveToFile(
        file: File,
        asBinary: Boolean = false,
    ) {
        file.writeText(serialize())
    }

    /** Serializes this node and its subtree to a text VDF string. */
    fun serialize(): String = StringBuilder().also { writeNode(it, this, 0) }.toString()

    private fun writeNode(
        sb: StringBuilder,
        kv: KeyValue,
        depth: Int,
    ) {
        val indent = "\t".repeat(depth)
        if (kv.children.isEmpty() && !kv.isSection) {
            sb.append(indent).append(quote(kv.name.orEmpty()))
                .append("\t\t").append(quote(kv.value.orEmpty())).append('\n')
        } else {
            sb.append(indent).append(quote(kv.name.orEmpty())).append('\n')
            sb.append(indent).append("{\n")
            for (child in kv.children) writeNode(sb, child, depth + 1)
            sb.append(indent).append("}\n")
        }
    }

    companion object {
        /** Sentinel returned by [get] for missing children. Compare with `===`. */
        val INVALID = KeyValue()

        private fun quote(s: String): String {
            val sb = StringBuilder(s.length + 2)
            sb.append('"')
            for (ch in s) {
                when (ch) {
                    '"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\n' -> sb.append("\\n")
                    '\t' -> sb.append("\\t")
                    else -> sb.append(ch)
                }
            }
            sb.append('"')
            return sb.toString()
        }

        /** Parses a text VDF document, returning null on failure. */
        fun loadFromString(text: String): KeyValue? =
            try {
                Parser(text).parseRoot()
            } catch (e: Exception) {
                null
            }
    }

    /** Text VDF tokenizer and recursive-descent parser. */
    private class Parser(
        private val s: String,
    ) {
        private var i = 0

        fun parseRoot(): KeyValue? {
            val name = nextToken() ?: return null
            val root = KeyValue(name)
            skipWhitespaceAndComments()
            if (i < s.length && s[i] == '{') {
                root.isSection = true
                i++
                parseChildren(root)
            } else {
                // Support a degenerate `"name" "value"` document.
                root.value = nextToken()
            }
            return root
        }

        private fun parseChildren(parent: KeyValue) {
            while (true) {
                skipWhitespaceAndComments()
                if (i >= s.length) return
                if (s[i] == '}') {
                    i++
                    return
                }
                val key = nextToken() ?: return
                val node = KeyValue(key)
                skipWhitespaceAndComments()
                if (i < s.length && s[i] == '{') {
                    node.isSection = true
                    i++
                    parseChildren(node)
                } else {
                    node.value = nextToken()
                    // Optional platform conditions may follow values.
                    skipWhitespaceAndComments()
                    if (i < s.length && s[i] == '[') skipConditional()
                }
                parent.children.add(node)
            }
        }

        private fun skipConditional() {
            while (i < s.length && s[i] != ']') i++
            if (i < s.length) i++
        }

        private fun skipWhitespaceAndComments() {
            while (i < s.length) {
                val c = s[i]
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    i++
                } else if (c == '/' && i + 1 < s.length && s[i + 1] == '/') {
                    while (i < s.length && s[i] != '\n') i++
                } else {
                    return
                }
            }
        }

        /** Reads the next quoted or unquoted token, or null at EOF or a brace. */
        private fun nextToken(): String? {
            skipWhitespaceAndComments()
            if (i >= s.length) return null
            val c = s[i]
            if (c == '{' || c == '}') return null
            return if (c == '"') readQuoted() else readUnquoted()
        }

        private fun readQuoted(): String {
            i++
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i]
                when {
                    c == '"' -> {
                        i++
                        return sb.toString()
                    }
                    c == '\\' && i + 1 < s.length -> {
                        when (s[i + 1]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            else -> sb.append(s[i + 1])
                        }
                        i += 2
                    }
                    else -> {
                        sb.append(c)
                        i++
                    }
                }
            }
            return sb.toString()
        }

        private fun readUnquoted(): String {
            val start = i
            while (i < s.length) {
                val c = s[i]
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n' ||
                    c == '{' || c == '}' || c == '"'
                ) {
                    break
                }
                i++
            }
            return s.substring(start, i)
        }
    }
}
