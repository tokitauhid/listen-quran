package com.listenquran.update

/**
 * Pure Kotlin semantic version (`MAJOR.MINOR.PATCH`) parser and comparator
 * supporting optional `v` prefixes (e.g. `v1.4.0` -> `1.4.0`).
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {

    override fun compareTo(other: SemVer): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val SEMVER_REGEX = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$")

        fun parse(raw: String?): SemVer? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val match = SEMVER_REGEX.matchEntire(trimmed) ?: return null
            val major = match.groupValues[1].toIntOrNull() ?: return null
            val minor = match.groupValues[2].toIntOrNull() ?: return null
            val patch = match.groupValues[3].toIntOrNull() ?: return null
            return SemVer(major, minor, patch)
        }
    }
}
