package com.winlator.cmod.feature.stores.steam.utils

import java.io.File

/**
 * In-house Valve KeyValues (text VDF) node + parser/serializer — the Phase 9
 * replacement for `in.dragonbra.javasteam.types.KeyValue` (text-VDF subset).
 *
 * Used for the Steam config files WinNative reads/edits: `localconfig.vdf`,
 * `appmanifest_*.acf`, `config.vdf` / `local.vdf`, and the Steam Input
 * controller-config manifest. Mirrors the JavaSteam `KeyValue` API the app
 * relied on: [name]/[value]/[children], `kv["key"]` lookup with the
 * [INVALID] sentinel, [asString], [loadFromString] and [saveToFile].
 *
 * Only the TEXT VDF form is handled (every caller uses text); the binary
 * KeyValues format is not needed here.
 */
class KeyValue(
    var name: String? = null,
    var value: String? = null,
) {
    val children: MutableList<KeyValue> = mutableListOf()

    /** True when this node is a `{ }` section rather than a `"k" "v"` leaf.
     *  Set by the parser and implied once children are added. */
    var isSection: Boolean = false

    /**
     * Child lookup by case-insensitive name. Returns the [INVALID] sentinel
     * (never null) when absent, so `kv["a"]["b"]["c"]` chains safely. Also
     * callable as `kv.get("a")`.
     */
    operator fun get(key: String): KeyValue =
        children.firstOrNull { it.name.equals(key, ignoreCase = true) } ?: INVALID

    /** The node's value as a string — null for a section or a missing node. */
    fun asString(): String? = value

    /**
     * Serialize this node as text VDF to [file]. [asBinary] is accepted for
     * call-site compatibility but ignored — only the text form is written.
     */
    fun saveToFile(
        file: File,
        asBinary: Boolean = false,
    ) {
        file.writeText(serialize())
    }

    /** Serialize this node (and its subtree) to a text-VDF string. */
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
        /** Sentinel returned by [get] for a missing child — compare with `===`. */
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

        /** Parse a text-VDF document; returns the root node, or null on failure. */
        fun loadFromString(text: String): KeyValue? =
            try {
                Parser(text).parseRoot()
            } catch (e: Exception) {
                null
            }
    }

    /** Text-VDF tokenizer + recursive-descent parser. */
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
                // Degenerate "name" "value" document.
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
                    // An optional [$CONDITIONAL] platform tag may follow a value.
                    skipWhitespaceAndComments()
                    if (i < s.length && s[i] == '[') skipConditional()
                }
                parent.children.add(node)
            }
        }

        private fun skipConditional() {
            while (i < s.length && s[i] != ']') i++
            if (i < s.length) i++ // consume ']'
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

        /** Read the next quoted or unquoted token, or null at EOF / a brace. */
        private fun nextToken(): String? {
            skipWhitespaceAndComments()
            if (i >= s.length) return null
            val c = s[i]
            if (c == '{' || c == '}') return null
            return if (c == '"') readQuoted() else readUnquoted()
        }

        private fun readQuoted(): String {
            i++ // opening quote
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
