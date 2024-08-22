package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.core.Versioned

/**
 * Functionality for supporting exposing of component [Version]s. Also contains other misc methods that have no other
 * place to live in.
 *
 * Note that this class can be used in two roles: first, as a static utility class for loading purposes, and second, as
 * a singleton loader of per-module version information.
 */
object VersionUtil {

    private val V_SEP = Regex("[-_./;:]")

    /**
     * Loads version information by introspecting a class named "PackageVersion" in the same package as the given class.
     *
     * If the class could not be found or does not have a public static Version field named "VERSION", returns "empty"
     * [Version] returned by [Version.unknownVersion].
     *
     * @param clazz Class for which to look version information
     *
     * @return Version information discovered if any; [Version.unknownVersion] if none
     */
    fun versionFor(clazz: Class<*>): Version {
        val version = try {
            val versionInfoClassName = "${clazz.`package`.name}.PackageVersion"
            val versionClass = Class.forName(versionInfoClassName, true, clazz.classLoader)
            (versionClass.getConstructor().newInstance() as Versioned).version()
        } catch (e: Exception) {
            null
        }

        return version ?: Version.unknownVersion()
    }

    /**
     * Method used by `PackageVersion` classes to decode version injected by build.
     *
     * @param versionString Version String to parse
     *
     * @param groupId Maven group id to include with version
     *
     * @param artifactId Maven artifact id to include with version
     *
     * @return Version instance constructed from parsed components, if successful; [Version.unknownVersion] if parsing
     * of components fail
     */
    fun parseVersion(versionString: String?, groupId: String?, artifactId: String?): Version {
        val realVersion = versionString?.trim() ?: return Version.unknownVersion()

        if (realVersion.isEmpty()) {
            return Version.unknownVersion()
        }

        val parts = V_SEP.split(realVersion)
        val major = parseVersionPart(parts[0])
        val minor = parseVersionPart(parts.getOrNull(1) ?: "")
        val patch = parseVersionPart(parts.getOrNull(2) ?: "")
        val snapshot = parts.getOrNull(3)

        return Version(major, minor, patch, snapshot, groupId, artifactId)
    }

    internal fun parseVersionPart(string: String): Int {
        var number = 0

        for (c in string) {
            if (c !in '0'..'9') {
                break
            }

            number = number * 10 + (c - '0')
        }

        return number
    }

}