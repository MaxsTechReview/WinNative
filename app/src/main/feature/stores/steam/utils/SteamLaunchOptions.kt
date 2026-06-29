package com.winlator.cmod.feature.stores.steam.utils

/** Parses Steam-style launch options: KEY=VALUE before %command% become env vars; args after become game args. */
object SteamLaunchOptions {
    private val ENV_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private const val COMMAND = "%command%"

    data class Parsed(val env: LinkedHashMap<String, String>, val gameArgs: String)

    @JvmStatic
    fun parse(execArgs: String?): Parsed {
        val env = LinkedHashMap<String, String>()
        val raw = execArgs?.trim().orEmpty()
        if (raw.isEmpty()) return Parsed(env, "")
        val idx = raw.indexOf(COMMAND)
        if (idx < 0) return Parsed(env, raw)
        val before = raw.substring(0, idx).trim()
        val after = raw.substring(idx + COMMAND.length).trim()
        for (token in tokenize(before)) {
            val eq = token.indexOf('=')
            if (eq <= 0) continue
            val key = token.substring(0, eq)
            if (ENV_KEY.matches(key)) env[key] = token.substring(eq + 1)
        }
        return Parsed(env, after)
    }

    /** Command-line arguments to pass to the game executable. */
    @JvmStatic
    fun gameArgs(execArgs: String?): String = parse(execArgs).gameArgs

    /** Environment variables to apply to the game process. */
    @JvmStatic
    fun parseEnvVars(execArgs: String?): Map<String, String> = parse(execArgs).env

    private fun tokenize(s: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        var quote: Char? = null
        for (c in s) {
            when {
                quote != null -> if (c == quote) quote = null else sb.append(c)
                c == '"' || c == '\'' -> quote = c
                c.isWhitespace() -> if (sb.isNotEmpty()) {
                    out.add(sb.toString()); sb.setLength(0)
                }
                else -> sb.append(c)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }
}
