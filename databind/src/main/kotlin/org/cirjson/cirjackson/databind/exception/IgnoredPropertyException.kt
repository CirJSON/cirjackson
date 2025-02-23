package org.cirjson.cirjackson.databind.exception

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DatabindException

/**
 * Specialized [DatabindException] subclass used to indicate case where an explicitly ignored property is encountered,
 * and mapper is configured to consider this an error.
 */
class IgnoredPropertyException(parser: CirJsonParser, message: String, location: CirJsonLocation,
        referringClass: Class<*>, propertyName: String, propertyIds: Collection<Any>?) :
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
         * @return The newly constructed IgnoredPropertyException
         */
        fun from(parser: CirJsonParser, fromObjectOrClass: Any, propertyName: String,
                propertyIds: Collection<Any>?): IgnoredPropertyException {
            val ref = if (fromObjectOrClass is Class<*>) fromObjectOrClass else fromObjectOrClass::class.java
            val message =
                    "Ignored field \"$propertyName\" (class ${ref.name}) encountered; mapper configured not to allow this"
            val e = IgnoredPropertyException(parser, message, parser.currentLocation(), ref, propertyName, propertyIds)
            e.prependPath(fromObjectOrClass, propertyName)
            return e
        }

    }

}