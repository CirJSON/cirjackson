package org.cirjson.cirjackson.databind.exception

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser
import java.util.*

/**
 * Base class for [MismatchedInputExceptions][MismatchedInputException] that are specifically related to problems
 * related to binding an individual property.
 *
 * @property referringClass Type (class) that is missing definition to allow binding of the unrecognized property.
 *
 * @property propertyName Logical property name that could not be mapped. Note that it is the last path reference in the
 * underlying path.
 *
 * @property propertyIds Set of ids of properties that are known for the type, if this can be statically determined.
 */
abstract class PropertyBindingException protected constructor(parser: CirJsonParser?, message: String,
        location: CirJsonLocation, val referringClass: Class<*>, val propertyName: String,
        protected val propertyIds: Collection<Any>?) : MismatchedInputException(parser, message, location) {

    /**
     * Lazily constructed description of known properties, used for constructing actual message if and as needed.
     */
    protected var myPropertiesAsString: String? = null

    override val messageSuffix: String?
        get() {
            var suffix = myPropertiesAsString

            if (suffix == null && propertyIds != null) {
                val stringBuilder = StringBuilder(100)
                val len = propertyIds.size

                if (len == 1) {
                    stringBuilder.append(" (one known property: \"")
                    stringBuilder.append(propertyIds.first())
                    stringBuilder.append('"')
                } else {
                    stringBuilder.append(" ($len known properties: ")
                    val iterator = propertyIds.iterator()

                    while (iterator.hasNext()) {
                        stringBuilder.append('"')
                        stringBuilder.append(iterator.next())
                        stringBuilder.append('"')

                        if (stringBuilder.length > MAX_DESC_LENGTH) {
                            stringBuilder.append(" [truncated]")
                            break
                        }

                        if (iterator.hasNext()) {
                            stringBuilder.append(", ")
                        }
                    }
                }

                stringBuilder.append("])")
                suffix = stringBuilder.toString().also { myPropertiesAsString = it }
            }

            return suffix
        }

    val knownPropertyIds: Collection<Any>?
        get() {
            propertyIds ?: return null
            return Collections.unmodifiableCollection(propertyIds)
        }

    companion object {

        /**
         * Somewhat arbitrary limit, but let's try not to create uselessly huge error messages
         */
        private const val MAX_DESC_LENGTH = 1000

    }

}