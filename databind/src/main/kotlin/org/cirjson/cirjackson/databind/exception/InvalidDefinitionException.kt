package org.cirjson.cirjackson.databind.exception

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DatabindException
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition

/**
 * Intermediate exception type used as the base class for all [DatabindExceptions][DatabindException] that are due to
 * problems with target type definition; usually a problem with annotations used on a class or its properties. This is
 * in contrast to [MismatchedInputException] which signals a problem with input to map.
 */
open class InvalidDefinitionException : DatabindException {

    /**
     * Fully resolved type that had the problem; this should always be known and available, never `null`.
     */
    val type: KotlinType?

    /**
     * Type definition (class) that had the definition problem, if any; may sometimes be undefined or unknown; if so,
     * returns `null`.
     */
    var beanDescription: BeanDescription?
        protected set

    /**
     * Property that had the definition problem if any (none, for example if the problem relates to type in general), if
     * known. If not known (or relevant), returns `null`.
     */
    var property: BeanPropertyDefinition?
        protected set

    protected constructor(parser: CirJsonParser?, message: String, type: KotlinType) : super(parser, message) {
        this.type = type
        beanDescription = null
        property = null
    }

    protected constructor(parser: CirJsonParser?, message: String, beanDescription: BeanDescription?,
            property: BeanPropertyDefinition?) : super(parser, message) {
        type = beanDescription?.type
        this.beanDescription = beanDescription
        this.property = property
    }

    protected constructor(generator: CirJsonGenerator?, message: String, type: KotlinType) : super(generator, message) {
        this.type = type
        beanDescription = null
        property = null
    }

    protected constructor(generator: CirJsonGenerator?, message: String, beanDescription: BeanDescription?,
            property: BeanPropertyDefinition?) : super(generator, message) {
        type = beanDescription?.type
        this.beanDescription = beanDescription
        this.property = property
    }

    companion object {

        fun from(parser: CirJsonParser?, message: String, type: KotlinType): InvalidDefinitionException {
            return InvalidDefinitionException(parser, message, type)
        }

        fun from(parser: CirJsonParser?, message: String, beanDescription: BeanDescription?,
                property: BeanPropertyDefinition?): InvalidDefinitionException {
            return InvalidDefinitionException(parser, message, beanDescription, property)
        }

        fun from(generator: CirJsonGenerator?, message: String, type: KotlinType): InvalidDefinitionException {
            return InvalidDefinitionException(generator, message, type)
        }

        fun from(generator: CirJsonGenerator?, message: String, beanDescription: BeanDescription?,
                property: BeanPropertyDefinition?): InvalidDefinitionException {
            return InvalidDefinitionException(generator, message, beanDescription, property)
        }

    }

}