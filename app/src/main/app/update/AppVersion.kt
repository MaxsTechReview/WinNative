package com.winlator.cmod.app.update

data class AppVersion(
    val numbers: List<Int>,
    val preRelease: String,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        val width = maxOf(numbers.size, other.numbers.size)
        for (i in 0 until width) {
            val mine = numbers.getOrElse(i) { 0 }
            val theirs = other.numbers.getOrElse(i) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        if (preRelease == other.preRelease) return 0
        if (preRelease.isEmpty()) return 1
        if (other.preRelease.isEmpty()) return -1
        return preRelease.compareTo(other.preRelease)
    }

    companion object {
        private val PATTERN = Regex("^[vV]?(\\d+(?:\\.\\d+)*)(?:[-+.](.*))?$")

        fun parse(raw: String?): AppVersion? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val match = PATTERN.matchEntire(trimmed) ?: return null
            val numbers =
                match.groupValues[1]
                    .split('.')
                    .mapNotNull { it.toIntOrNull() }
            if (numbers.isEmpty()) return null
            return AppVersion(numbers, match.groupValues.getOrElse(2) { "" }.lowercase())
        }
    }
}
