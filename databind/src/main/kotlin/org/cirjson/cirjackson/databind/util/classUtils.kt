package org.cirjson.cirjackson.databind.util

/*
 ***********************************************************************************************************************
 * Class type detection methods
 ***********************************************************************************************************************
 */

/**
 * Helper accessor for detecting Java14-added new {@code Record} types
 */
val Class<*>.isRecordType: Boolean
    get() {
        val parent = superclass ?: return false
        return parent.name == "java.lang.Record"
    }

/*
 ***********************************************************************************************************************
 * Enum type detection
 ***********************************************************************************************************************
 */

/**
 * Helper accessor that encapsulates reliable check on whether given raw type "is an Enum", that is, is or extends
 * [Enum].
 */
val Class<*>.isEnumType: Boolean
    get() = Enum::class.java.isAssignableFrom(this)
