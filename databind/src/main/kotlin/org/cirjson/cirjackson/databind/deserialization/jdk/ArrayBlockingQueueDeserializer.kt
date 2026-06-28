package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.NullValueProvider
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import java.util.concurrent.ArrayBlockingQueue

/**
 * We need a custom deserializer both because [ArrayBlockingQueue] has no default constructor AND because it has size
 * limit used for constructing underlying storage automatically.
 */
open class ArrayBlockingQueueDeserializer : CollectionDeserializer {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(collectionType: KotlinType, valueDeserializer: ValueDeserializer<Any>?,
            valueTypeDeserializer: TypeDeserializer?, valueInstantiator: ValueInstantiator?) : super(collectionType,
            valueDeserializer, valueTypeDeserializer, valueInstantiator)

    /**
     * Constructor used when creating contextualized instances.
     */
    protected constructor(collectionType: KotlinType, valueDeserializer: ValueDeserializer<Any>?,
            valueTypeDeserializer: TypeDeserializer?, valueInstantiator: ValueInstantiator?,
            delegateDeserializer: ValueDeserializer<Any>?, nullValueProvider: NullValueProvider?,
            unwrapSingle: Boolean?) : super(collectionType, valueDeserializer, valueTypeDeserializer, valueInstantiator,
            delegateDeserializer, nullValueProvider, unwrapSingle)

    /**
     * Copy-constructor that can be used by subclasses to allow copy-on-write styling copying of settings of an existing
     * instance.
     */
    protected constructor(source: ArrayBlockingQueueDeserializer) : super(source)

    @Suppress("UNCHECKED_CAST")
    override fun withResolved(delegateDeserializer: ValueDeserializer<*>?, valueDeserializer: ValueDeserializer<*>?,
            valueTypeDeserializer: TypeDeserializer?, nullValueProvider: NullValueProvider?,
            unwrapSingle: Boolean?): ArrayBlockingQueueDeserializer {
        return ArrayBlockingQueueDeserializer(myContainerType, valueDeserializer as ValueDeserializer<Any>?,
                valueTypeDeserializer, myValueInstantiator, delegateDeserializer as ValueDeserializer<Any>?,
                nullValueProvider, unwrapSingle)
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun createDefaultInstance(context: DeserializationContext): MutableCollection<Any?>? {
        return null
    }

    @Throws(CirJacksonException::class)
    override fun deserializeFromArray(parser: CirJsonParser, context: DeserializationContext,
            result: MutableCollection<Any?>?): MutableCollection<Any?>? {
        val toUse = super.deserializeFromArray(parser, context, result ?: ArrayList())!!

        return if (toUse.isEmpty()) {
            ArrayBlockingQueue<Any>(1, false)
        } else {
            ArrayBlockingQueue<Any>(toUse.size, false, toUse)
        }
    }

}