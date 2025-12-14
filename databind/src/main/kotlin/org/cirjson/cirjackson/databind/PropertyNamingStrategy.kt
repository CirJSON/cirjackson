package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedField
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.introspection.AnnotatedParameter

/**
 * Class that defines how names of JSON properties ("external names") are derived from names of POJO methods and fields
 * ("internal names"), in cases where no explicit annotations exist for naming. Methods are passed information about
 * POJO member for which name is needed, as well as default name that would be used if no custom strategy was used.
 * 
 * Default (empty) implementation returns suggested ("implicit" or "default") name unmodified
 * 
 * Note that the strategy is guaranteed to be called once per logical property (which may be represented by multiple
 * members; such as pair of a getter and a setter), but may be called for each: implementations should not count on
 * exact number of times, and should work for any member that represent a property. Also note that calls are made during
 * construction of serializers and deserializers which are typically cached, and not for every time serializer or
 * deserializer is called.
 * 
 * In absence of a registered custom strategy, the default Java property naming strategy is used, which leaves field
 * names as is, and removes set/get/is prefix from methods (as well as lower-cases initial sequence of capitalized
 * characters).
 */
open class PropertyNamingStrategy {

    /**
     * Method called to find external name (name used in JSON) for given logical POJO property, as defined by given
     * field.
     *
     * @param config Configuration in used: either `SerializationConfig` or `DeserializationConfig`, depending on
     * whether method is called during serialization or deserialization
     * 
     * @param field Field used to access property
     * 
     * @param defaultName Default name that would be used for property in absence of custom strategy
     *
     * @return Logical name to use for property that the field represents
     */
    open fun nameForField(config: MapperConfig<*>, field: AnnotatedField, defaultName: String): String {
        return defaultName
    }

    /**
     * Method called to find external name (name used in JSON) for given logical POJO property, as defined by given
     * getter method; typically called when building a serializer. (but not always -- when using "getter-as-setter", may
     * be called during deserialization)
     *
     * @param config Configuration in used: either `SerializationConfig` or `DeserializationConfig`, depending on
     * whether method is called during serialization or deserialization
     *
     * @param method Method used to access property.
     *
     * @param defaultName Default name that would be used for property in absence of custom strategy
     *
     * @return Logical name to use for property that the method represents
     */
    open fun nameForGetterMethod(config: MapperConfig<*>, method: AnnotatedMethod, defaultName: String): String {
        return defaultName
    }

    /**
     * Method called to find external name (name used in JSON) for given logical POJO property, as defined by given
     * setter method; typically called when building a deserializer (but not necessarily only then).
     *
     * @param config Configuration in used: either `SerializationConfig` or `DeserializationConfig`, depending on
     * whether method is called during serialization or deserialization
     *
     * @param method Method used to access property.
     *
     * @param defaultName Default name that would be used for property in absence of custom strategy
     *
     * @return Logical name to use for property that the method represents
     */
    open fun nameForSetterMethod(config: MapperConfig<*>, method: AnnotatedMethod, defaultName: String): String {
        return defaultName
    }

    /**
     * Method called to find external name (name used in JSON) for given logical POJO property, as defined by given
     * constructor parameter; typically called when building a deserializer (but not necessarily only then).
     *
     * @param config Configuration in used: either `SerializationConfig` or `DeserializationConfig`, depending on
     * whether method is called during serialization or deserialization
     *
     * @param constructorParameter Constructor parameter used to pass property.
     *
     * @param defaultName Default name that would be used for property in absence of custom strategy
     */
    open fun nameForConstructorParameter(config: MapperConfig<*>, constructorParameter: AnnotatedParameter,
            defaultName: String): String {
        return defaultName
    }

}