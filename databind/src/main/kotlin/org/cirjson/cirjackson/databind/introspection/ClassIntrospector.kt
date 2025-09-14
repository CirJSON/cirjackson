package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig

/**
 * Helper class used to introspect features of POJO value classes used with CirJackson. The main use is for finding out
 * POJO construction (creator) and value access (getters, setters) methods and annotations that define configuration of
 * using those methods.
 */
abstract class ClassIntrospector protected constructor() {

    /**
     * Method called to create an instance to be exclusive used by specified mapper. Needed to ensure that no sharing
     * through cache occurs.
     *
     * Basic implementation just returns instance itself.
     */
    abstract fun forMapper(): ClassIntrospector

    /**
     * Method called to further create an instance to be used for a single operation (read or write, typically matching
     * [ObjectMapper][org.cirjson.cirjackson.databind.ObjectMapper] `readValue()` or `writeValue()`).
     */
    abstract fun forOperation(config: MapperConfig<*>): ClassIntrospector

    /*
     *******************************************************************************************************************
     * Public API: annotation introspection
     *******************************************************************************************************************
     */

    /**
     * Factory method that constructs an introspector that only has information regarding annotations class itself (or
     * its supertypes) has, but nothing on methods or constructors.
     */
    abstract fun introspectClassAnnotations(type: KotlinType): AnnotatedClass

    /**
     * Factory method that constructs an introspector that only has information regarding annotations class itself has
     * (but NOT including its supertypes), but nothing on methods or constructors.
     */
    abstract fun introspectDirectClassAnnotations(type: KotlinType): AnnotatedClass

    /*
     *******************************************************************************************************************
     * Public API: bean property introspection
     *******************************************************************************************************************
     */

    /**
     * Factory method that constructs an introspector that has all information needed for serialization purposes.
     */
    abstract fun introspectForSerialization(type: KotlinType): BeanDescription

    /**
     * Factory method that constructs an introspector that has all information needed for deserialization purposes.
     */
    abstract fun introspectForDeserialization(type: KotlinType): BeanDescription

    /**
     * Factory method that constructs an introspector that has all information needed for constructing deserializers
     * that use intermediate Builder objects.
     */
    abstract fun introspectForDeserializationWithBuilder(type: KotlinType,
            valueTypeDescription: BeanDescription): BeanDescription

    /**
     * Factory method that constructs an introspector that has information necessary for creating instances of given
     * class ("creator"), as well as class annotations, but no information on member methods
     */
    abstract fun introspectForCreation(type: KotlinType): BeanDescription

}