package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.Version

object VersionUtil {

    private val V_SEP = Regex("[-_./;:]")

    fun parseVersion(versionString: String?, groupId: String?, artifactId: String?): Version {
        val realVersion = versionString?.trim() ?: return Version.unknownVersion()

        if (realVersion.isEmpty()) {
            return Version.unknownVersion()
        }

        val parts = V_SEP.split(realVersion)
        val major = parts[0].toInt()
        val minor = parts.getOrNull(1)?.toInt() ?: 0
        val patch = parts.getOrNull(2)?.toInt() ?: 0
        val snapshot = parts.getOrNull(3)

        return Version(major, minor, patch, snapshot, groupId, artifactId)
    }

}