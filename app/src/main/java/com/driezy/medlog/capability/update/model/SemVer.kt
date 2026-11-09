package com.driezy.medlog.capability.update.model

/**
 * Parses and compares semantic versions conforming to SemVer 2.0.0.
 * Examples: "1.26.0", "v1.26.0", "1.26.0-rc1", "1.26.0-beta.2".
 */
data class SemVer(val major: Int, val minor: Int, val patch: Int, val preRelease: String? = null) :
    Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)

        // When major.minor.patch are equal, a normal release has higher precedence
        // than a pre-release (e.g. 1.26.0 > 1.26.0-rc1).
        return when {
            preRelease == null && other.preRelease == null -> 0
            preRelease == null && other.preRelease != null -> 1
            preRelease != null && other.preRelease == null -> -1
            else -> comparePreRelease(preRelease ?: "", other.preRelease ?: "")
        }
    }

    private fun comparePreRelease(a: String, b: String): Int {
        val aTokens = a.split(".")
        val bTokens = b.split(".")
        val maxLen = maxOf(aTokens.size, bTokens.size)
        for (i in 0 until maxLen) {
            val aTok = aTokens.getOrNull(i) ?: return -1
            val bTok = bTokens.getOrNull(i) ?: return 1
            val aInt = aTok.toIntOrNull()
            val bInt = bTok.toIntOrNull()
            val cmp = when {
                aInt != null && bInt != null -> aInt.compareTo(bInt)
                aInt != null -> -1
                bInt != null -> 1
                else -> aTok.compareTo(bTok)
            }
            if (cmp != 0) return cmp
        }
        return 0
    }

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        if (!preRelease.isNullOrBlank()) {
            append("-$preRelease")
        }
    }

    companion object {
        fun parseOrNull(version: String?): SemVer? {
            if (version.isNullOrBlank()) return null
            val trimmed = version.trim().removePrefix("v").removePrefix("V")
            val base = trimmed.substringBefore("-")
            val pre = if (trimmed.contains("-")) trimmed.substringAfter("-") else null
            val parts = base.split(".").mapNotNull { it.toIntOrNull() }
            if (parts.size < 2) return null
            val major = parts[0]
            val minor = parts[1]
            val patch = parts.getOrNull(2) ?: 0
            return SemVer(major, minor, patch, pre)
        }
    }
}
