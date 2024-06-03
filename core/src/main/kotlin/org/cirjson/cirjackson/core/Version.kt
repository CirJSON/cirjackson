package org.cirjson.cirjackson.core

import java.io.Serializable

/**
 * Object that encapsulates versioning information of a component.
 * Version information includes not just version number but also
 * optionally group and artifact ids of the component being versioned.
 *
 * Note that optional group and artifact id properties are new with Jackson 2.0:
 * if provided, they should align with Maven artifact information.
 */
class Version(val majorVersion: Int, val minorVersion: Int, val patchLevel: Int, val snapshotInfo: String?,
        groupId: String?, artifactId: String?) : Comparable<Version>, Serializable {

    val groupId = groupId ?: ""

    val artifactId = artifactId ?: ""

    /**
     * `true` if this instance is the one returned by call to [unknownVersion]
     */
    val isUnknownVersion: Boolean
        get() = this === UNKNOWN_VERSION

    val isSnapshot: Boolean
        get() = !snapshotInfo.isNullOrEmpty()

    fun toFullString(): String {
        return "$groupId/$artifactId/${toString()}"
    }

    override fun compareTo(other: Version): Int {
        if (this === other) {
            return 0
        }

        return groupId.compareTo(other.groupId).takeIf { it != 0 } ?: artifactId.compareTo(other.artifactId)
                .takeIf { it != 0 } ?: majorVersion.compareTo(other.majorVersion).takeIf { it != 0 }
        ?: minorVersion.compareTo(other.minorVersion).takeIf { it != 0 } ?: patchLevel.compareTo(other.patchLevel)
                .takeIf { it != 0 }
        ?: if (isSnapshot) {
            if (other.isSnapshot) {
                snapshotInfo!!.compareTo(other.snapshotInfo!!)
            } else {
                -1
            }
        } else if (other.isSnapshot) {
            1
        } else {
            0
        }
    }

    override fun toString(): String {
        return StringBuilder().apply {
            append(majorVersion).append('.')
            append(minorVersion).append('.')
            append(patchLevel)

            if (isSnapshot) {
                append('-').append(snapshotInfo)
            }
        }.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (javaClass != other?.javaClass) {
            return false
        }

        other as Version

        if (majorVersion != other.majorVersion) {
            return false
        }

        if (minorVersion != other.minorVersion) {
            return false
        }

        if (patchLevel != other.patchLevel) {
            return false
        }

        if (snapshotInfo != other.snapshotInfo) {
            return false
        }

        if (groupId != other.groupId) {
            return false
        }

        if (artifactId != other.artifactId) {
            return false
        }

        return true
    }

    override fun hashCode(): Int {
        var result = majorVersion
        result = 31 * result + minorVersion
        result = 31 * result + patchLevel
        result = 31 * result + (snapshotInfo?.hashCode() ?: 0)
        result = 31 * result + groupId.hashCode()
        result = 31 * result + artifactId.hashCode()
        return result
    }


    companion object {

        private val UNKNOWN_VERSION = Version(0, 0, 0, null, null, null)

        /**
         * Method returns canonical "not known" version, which is used as version in cases where actual version
         * information is not known (instead of null).
         *
         * @return Version instance to use as a placeholder when actual version is not known (or not relevant)
         */
        fun unknownVersion(): Version {
            return UNKNOWN_VERSION
        }

    }

}