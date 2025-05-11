package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.databind.util.name
import kotlin.reflect.KClass

/**
 * Helper class for [UnresolvedForwardReferenceException], to contain information about unresolved ids.
 *
 * @property id The id which is unresolved.
 *
 * @property type The type of object which was expected.
 *
 * @property location The location in the CirJSON being parsed
 */
class UnresolvedId(val id: Any, val type: KClass<*>, val location: CirJsonLocation) {

    override fun toString(): String {
        return "Object id [$id] (for ${type.name}) at $location"
    }

}