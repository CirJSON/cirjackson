package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdResolver
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty

/**
 * Object that knows how to deserialize Object Ids.
 *
 * @property generator Blueprint generator instance: actual instance will be fetched from [DeserializationContext] using
 * this as the key.
 */
open class ObjectIdReader protected constructor(protected val myIdType: KotlinType, val propertyName: PropertyName,
        val generator: ObjectIdGenerator<*>, val resolver: ObjectIdResolver, deserializer: ValueDeserializer<*>,
        val idProperty: SettableBeanProperty?) {

    /**
     * Deserializer used for deserializing id values.
     */
    @Suppress("UNCHECKED_CAST")
    protected val myDeserializer = deserializer as ValueDeserializer<Any>

    /*
     *******************************************************************************************************************
     * API
     *******************************************************************************************************************
     */

    open val deserializer: ValueDeserializer<Any>
        get() = myDeserializer

    open val idType: KotlinType
        get() = myIdType

    /**
     * Convenience method, equivalent to calling:
     * ```
     * readerInstance.generator.maySerializeAsObject();
     * ```
     * and used to determine whether Object Ids handled by the underlying generator may be in form of (CirJSON) Objects.
     * Used for optimizing handling in cases where method returns `false`.
     */
    open fun maySerializeAsObject(): Boolean {
        return generator.maySerializeAsObject()
    }

    /**
     * Convenience method, equivalent to calling:
     * ```
     * readerInstance.generator.isValidReferencePropertyName(name, parser);
     * ```
     * and used to determine whether Object Ids handled by the underlying generator may be in form of (CirJSON) Objects.
     * Used for optimizing handling in cases where method returns `false`.
     */
    open fun isValidReferencePropertyName(name: String, parser: CirJsonParser): Boolean {
        return generator.isValidReferencePropertyName(name, parser)
    }

    /**
     * Method called to read value that is expected to be an Object Reference (that is, value of an Object ID used to
     * refer to another object).
     */
    @Throws(CirJacksonException::class)
    open fun readObjectReference(parser: CirJsonParser, context: DeserializationContext): Any? {
        return myDeserializer.deserialize(parser, context)
    }

    companion object {

        /**
         * Factory method called by [org.cirjson.cirjackson.databind.deserialization.bean.BeanDeserializerBase] with the
         * initial information based on standard settings for the type for which deserializer is being built.
         */
        fun construct(idType: KotlinType, propertyName: PropertyName, generator: ObjectIdGenerator<*>,
                resolver: ObjectIdResolver, deserializer: ValueDeserializer<*>,
                idProperty: SettableBeanProperty?): ObjectIdReader {
            return ObjectIdReader(idType, propertyName, generator, resolver, deserializer, idProperty)
        }

    }

}