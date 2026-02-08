package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer

/**
 * Abstract class that defines API used by [SerializerProvider] to obtain actual [ValueSerializer] instances from
 * multiple distinct factories.
 */
abstract class SerializerFactory {

    /*
     *******************************************************************************************************************
     * Basic API
     *******************************************************************************************************************
     */

    /**
     * Method called to create (or, for immutable serializers, reuse) a serializer for given type.
     *
     * @param context Context that needs to be used to resolve annotation-provided serializers (but NOT for others)
     * 
     * @param formatOverride Possible format overrides (from property annotations) to use, above and beyond what
     * `beanDescription` defines
     */
    abstract fun createSerializer(context: SerializerProvider, baseType: KotlinType, beanDescription: BeanDescription,
            formatOverride: CirJsonFormat.Value?): ValueSerializer<Any>

    /**
     * Method called to create serializer to use for serializing CirJSON property names (which must be output as
     * `CirJsonToken.FIELD_NAME`) for Map that has specified declared key type, and is for specified property (or, if
     * property is `null`, as root value)
     *
     * @param type Declared type for Map keys
     *
     * @return Serializer to use, if factory knows it; `null` if not (in which case default serializer is to be used)
     */
    abstract fun createKeySerializer(context: SerializerProvider, type: KotlinType): ValueSerializer<Any>?

    /**
     * Returns serializer used to (try to) output a `null` key, due to an entry of [Map] having `null` key. The default
     * implementation will throw an exception if this happens; alternative implementation (like one that would write an
     * Empty String) can be defined.
     */
    abstract val defaultNullKeySerializer: ValueSerializer<Any>

    abstract val defaultNullValueSerializer: ValueSerializer<Any>

    /*
     *******************************************************************************************************************
     * Mutant factories for registering additional configuration
     *******************************************************************************************************************
     */

    /**
     * Mutant factory method for creating a new factory instance with additional serializer provider: provider will get
     * inserted as the first one to be checked.
     */
    abstract fun withAdditionalSerializers(additional: Serializers): SerializerFactory

    /**
     * Mutant factory method for creating a new factory instance with additional key serializer provider: provider will
     * get inserted as the first one to be checked.
     */
    abstract fun withAdditionalKeySerializers(additional: Serializers): SerializerFactory

    /**
     * Mutant factory method for creating a new factory instance with additional serializer modifier: modifier will get
     * inserted as the first one to be checked.
     */
    abstract fun withSerializerModifier(modifier: ValueSerializerModifier): SerializerFactory

    abstract fun withNullKeySerializers(serializer: ValueSerializer<*>): SerializerFactory

    abstract fun withNullValueSerializers(serializer: ValueSerializer<*>): SerializerFactory

}