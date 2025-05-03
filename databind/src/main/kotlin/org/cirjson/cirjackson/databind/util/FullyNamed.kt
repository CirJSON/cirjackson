package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.core.util.Named
import org.cirjson.cirjackson.databind.PropertyName

/**
 * Extension over [Named] to expose full name; most relevant for formats like XML that use namespacing.
 */
interface FullyNamed : Named {

    val fullName: PropertyName

    fun hasName(name: PropertyName): Boolean {
        return fullName == name
    }

}