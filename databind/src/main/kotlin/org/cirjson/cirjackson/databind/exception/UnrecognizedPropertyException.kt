package org.cirjson.cirjackson.databind.exception

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser
import kotlin.reflect.KClass

/**
 * Specialized [PropertyBindingException] subclass specifically used to indicate problems due to encountering a CirJSON
 * property that could not be mapped to an Object property (via getter, constructor argument or field).
 */
open class UnrecognizedPropertyException(parser: CirJsonParser?, message: String, location: CirJsonLocation,
        referringClass: KClass<*>, propertyName: String, propertyIds: Collection<Any>?) :
        PropertyBindingException(parser, message, location, referringClass, propertyName, propertyIds) {

    companion object {

        /**
         * Factory method used for constructing instances of this exception type.
         *
         * @param parser Underlying parser used for reading input being used for data-binding
         *
         * @param fromObjectOrClass Reference to either instance of problematic type (if available), or if not, type
         * itself
         *
         * @param propertyName Name of unrecognized property
         *
         * @param propertyIds (optional, `null` if not available) Set of properties that type would recognize, if
         * completely known: `null` if set cannot be determined.
         *
         * @return The newly constructed UnrecognizedPropertyException
         */
        fun from(parser: CirJsonParser, fromObjectOrClass: Any, propertyName: String,
                propertyIds: Collection<Any>?): UnrecognizedPropertyException {
            val ref = fromObjectOrClass as? KClass<*> ?: fromObjectOrClass::class
            val message =
                    "Ignored field \"$propertyName\" (class ${ref.qualifiedName}) encountered; mapper configured not to allow this"
            val e = UnrecognizedPropertyException(parser, message, parser.currentLocation(), ref, propertyName,
                    propertyIds)
            e.prependPath(fromObjectOrClass, propertyName)
            return e
        }

    }

}