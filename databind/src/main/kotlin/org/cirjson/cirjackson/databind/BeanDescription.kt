package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition

/**
 * Basic container for information gathered by [ClassIntrospector] to help in constructing serializers and
 * deserializers. Note that the one implementation type is [BasicBeanDescription], meaning that it is safe to upcast to
 * that type.
 *
 * @property myType Bean type information, including raw class and possible generics information
 */
abstract class BeanDescription(protected val myType: KotlinType) {

    /*
     *******************************************************************************************************************
     * Simple accessors
     *******************************************************************************************************************
     */

    open val type: KotlinType
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Basic API for finding properties
     *******************************************************************************************************************
     */

    abstract fun findProperties(): List<BeanPropertyDefinition>

}