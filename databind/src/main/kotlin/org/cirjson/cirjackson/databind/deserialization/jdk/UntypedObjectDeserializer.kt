package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.isCirJacksonStandardImplementation

/**
 * Deserializer implementation that is used if it is necessary to bind content of "unknown" type; something declared as
 * basic [Any] (either explicitly, or due to type erasure). If so, "natural" mapping is used to convert CirJSON values
 * to their natural object matches: CirJSON arrays to [MutableLists][MutableList] (or, if configured, `Array<Any>`),
 * CirJSON objects to [MutableMaps][MutableMap], numbers to [Numbers][Number], booleans to [Booleans][Boolean] and
 * strings to [Strings][String] (and `null` to `null`).
 */
@CirJacksonStandardImplementation
open class UntypedObjectDeserializer : StandardDeserializer<Any> {

    /*
     *******************************************************************************************************************
     * Possible custom deserializer overrides we need to use
     *******************************************************************************************************************
     */

    protected var myMapDeserializer: ValueDeserializer<Any>? = null

    protected var myListDeserializer: ValueDeserializer<Any>? = null

    protected var myStringDeserializer: ValueDeserializer<Any>? = null

    protected var myNumberDeserializer: ValueDeserializer<Any>? = null

    /**
     * If [MutableList] has been mapped to non-default implementation, we'll store type here
     */
    protected val myListType: KotlinType?

    /**
     * If [MutableMap] has been mapped to non-default implementation, we'll store type here
     */
    protected val myMapType: KotlinType?

    protected val myNonMerging: Boolean

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(listType: KotlinType?, mapType: KotlinType?) : super(Any::class) {
        myListType = listType
        myMapType = mapType
        myNonMerging = false
    }

    @Suppress("UNCHECKED_CAST")
    protected constructor(source: UntypedObjectDeserializer, mapDeserializer: ValueDeserializer<*>?,
            listDeserializer: ValueDeserializer<*>?, stringDeserializer: ValueDeserializer<*>?,
            numberDeserializer: ValueDeserializer<*>?) : super(source) {
        myMapDeserializer = mapDeserializer as ValueDeserializer<Any>?
        myListDeserializer = listDeserializer as ValueDeserializer<Any>?
        myStringDeserializer = stringDeserializer as ValueDeserializer<Any>?
        myNumberDeserializer = numberDeserializer as ValueDeserializer<Any>?
        myListType = source.myListType
        myMapType = source.myMapType
        myNonMerging = source.myNonMerging
    }

    protected constructor(source: UntypedObjectDeserializer, nonMerging: Boolean) : super(source) {
        myMapDeserializer = source.myMapDeserializer
        myListDeserializer = source.myListDeserializer
        myStringDeserializer = source.myStringDeserializer
        myNumberDeserializer = source.myNumberDeserializer
        myListType = source.myListType
        myMapType = source.myMapType
        myNonMerging = nonMerging
    }

    /*
     *******************************************************************************************************************
     * Validation, post-processing (ResolvableDeserializer)
     *******************************************************************************************************************
     */

    /**
     * We need to implement this method to properly find things to delegate to: it cannot be done earlier since
     * delegated deserializers almost certainly require access to this instance (at least "MutableList" and "MutableMap"
     * ones)
     */
    @Suppress("UNCHECKED_CAST")
    override fun resolve(context: DeserializationContext) {
        val anyType = context.constructType(Any::class)!!
        val stringType = context.constructType(String::class)!!
        val typeFactory = context.typeFactory

        myListDeserializer = myListType?.let { findCustomDeserializer(context, it) } ?: clearIfStandardImplementation(
                findCustomDeserializer(context, typeFactory.constructCollectionType(MutableList::class, anyType)))
        myMapDeserializer = myMapType?.let { findCustomDeserializer(context, it) } ?: clearIfStandardImplementation(
                findCustomDeserializer(context, typeFactory.constructMapType(MutableMap::class, stringType, anyType)))
        myStringDeserializer = clearIfStandardImplementation(findCustomDeserializer(context, stringType))
        myNumberDeserializer = clearIfStandardImplementation(
                findCustomDeserializer(context, typeFactory.constructType(Number::class.java)))

        val unknownType = TypeFactory.unknownType()
        myMapDeserializer = context.handleSecondaryContextualization(myMapDeserializer, null,
                unknownType) as ValueDeserializer<Any>?
        myListDeserializer = context.handleSecondaryContextualization(myListDeserializer, null,
                unknownType) as ValueDeserializer<Any>?
        myStringDeserializer = context.handleSecondaryContextualization(myStringDeserializer, null,
                unknownType) as ValueDeserializer<Any>?
        myNumberDeserializer = context.handleSecondaryContextualization(myNumberDeserializer, null,
                unknownType) as ValueDeserializer<Any>?
    }

    protected open fun findCustomDeserializer(context: DeserializationContext,
            type: KotlinType): ValueDeserializer<Any> {
        return context.findNonContextualValueDeserializer(type)
    }

    protected open fun clearIfStandardImplementation(deserializer: ValueDeserializer<Any>?): ValueDeserializer<Any>? {
        return deserializer.takeUnless { it.isCirJacksonStandardImplementation }
    }

    /**
     * We only use contextualization for optimizing the case where no customization occurred; if so, can slip in a more
     * streamlined version.
     */
    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val nonMerging = property == null && context.config.getDefaultMergeable(Any::class) == false

        return if (myStringDeserializer == null && myNumberDeserializer == null && myMapDeserializer == null &&
                myListDeserializer == null) {
            UntypedObjectDeserializerNR.instance(nonMerging)
        } else if (myNonMerging == nonMerging) {
            UntypedObjectDeserializer(this, nonMerging)
        } else {
            this
        }
    }

    override val isCacheable: Boolean
        get() = true

    override fun logicalType(): LogicalType? {
        return LogicalType.UNTYPED
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_START_OBJECT, CirJsonTokenId.ID_PROPERTY_NAME, CirJsonTokenId.ID_END_OBJECT -> {
                val mapDeserializer = myMapDeserializer

                if (mapDeserializer != null) {
                    mapDeserializer.deserialize(parser, context)
                } else {
                    mapObject(parser, context)
                }
            }

            CirJsonTokenId.ID_START_ARRAY -> {
                if (context.isEnabled(DeserializationFeature.USE_JAVA_ARRAY_FOR_CIRJSON_ARRAY)) {
                    mapArrayToArray(parser, context)
                } else {
                    val listDeserializer = myListDeserializer

                    if (listDeserializer != null) {
                        listDeserializer.deserialize(parser, context)
                    } else {
                        mapArray(parser, context)
                    }
                }
            }

            CirJsonTokenId.ID_EMBEDDED_OBJECT -> {
                parser.embeddedObject
            }

            CirJsonTokenId.ID_STRING -> {
                val stringDeserializer = myStringDeserializer

                if (stringDeserializer != null) {
                    stringDeserializer.deserialize(parser, context)
                } else {
                    parser.text
                }
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                val numberDeserializer = myNumberDeserializer

                if (numberDeserializer != null) {
                    numberDeserializer.deserialize(parser, context)
                } else if (context.hasSomeOfFeatures(FEATURE_MASK_INT_COERCIONS)) {
                    coerceIntegral(parser, context)
                } else {
                    parser.numberValue
                }
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                val numberDeserializer = myNumberDeserializer

                if (numberDeserializer != null) {
                    numberDeserializer.deserialize(parser, context)
                } else {
                    deserializeFloatingPoint(parser, context)
                }
            }

            CirJsonTokenId.ID_TRUE -> {
                true
            }

            CirJsonTokenId.ID_FALSE -> {
                false
            }

            CirJsonTokenId.ID_NULL -> {
                null
            }

            else -> {
                context.handleUnexpectedToken(getValueType(context), parser)
            }
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_START_OBJECT, CirJsonTokenId.ID_START_ARRAY, CirJsonTokenId.ID_PROPERTY_NAME -> {
                typeDeserializer.deserializeTypedFromAny(parser, context)
            }

            CirJsonTokenId.ID_EMBEDDED_OBJECT -> {
                parser.embeddedObject
            }

            CirJsonTokenId.ID_STRING -> {
                val stringDeserializer = myStringDeserializer

                if (stringDeserializer != null) {
                    stringDeserializer.deserialize(parser, context)
                } else {
                    parser.text
                }
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                val numberDeserializer = myNumberDeserializer

                if (numberDeserializer != null) {
                    numberDeserializer.deserialize(parser, context)
                } else if (context.hasSomeOfFeatures(FEATURE_MASK_INT_COERCIONS)) {
                    coerceIntegral(parser, context)
                } else {
                    parser.numberValue
                }
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                val numberDeserializer = myNumberDeserializer

                if (numberDeserializer != null) {
                    numberDeserializer.deserialize(parser, context)
                } else {
                    deserializeFloatingPoint(parser, context)
                }
            }

            CirJsonTokenId.ID_TRUE -> {
                true
            }

            CirJsonTokenId.ID_FALSE -> {
                false
            }

            CirJsonTokenId.ID_NULL -> {
                null
            }

            else -> {
                context.handleUnexpectedToken(getValueType(context), parser)
            }
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: Any): Any? {
        if (myNonMerging) {
            return deserialize(parser, context)
        }

        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_START_OBJECT, CirJsonTokenId.ID_PROPERTY_NAME, CirJsonTokenId.ID_END_OBJECT -> {
                val mapDeserializer = myMapDeserializer

                if (mapDeserializer != null) {
                    mapDeserializer.deserialize(parser, context, intoValue)
                } else if (intoValue is MutableMap<*, *>) {
                    mapObject(parser, context, intoValue as MutableMap<Any?, Any?>)
                } else {
                    mapObject(parser, context)
                }
            }

            CirJsonTokenId.ID_START_ARRAY -> {
                val listDeserializer = myListDeserializer

                if (listDeserializer != null) {
                    listDeserializer.deserialize(parser, context, intoValue)
                } else if (intoValue is MutableCollection<*>) {
                    mapArray(parser, context, intoValue as MutableCollection<Any?>)
                } else if (context.isEnabled(DeserializationFeature.USE_JAVA_ARRAY_FOR_CIRJSON_ARRAY)) {
                    mapArrayToArray(parser, context)
                } else {
                    mapArray(parser, context)
                }
            }

            CirJsonTokenId.ID_STRING -> {
                val stringDeserializer = myStringDeserializer

                if (stringDeserializer != null) {
                    stringDeserializer.deserialize(parser, context, intoValue)
                } else {
                    parser.text
                }
            }

            CirJsonTokenId.ID_NUMBER_INT -> {
                val numberDeserializer = myNumberDeserializer

                if (numberDeserializer != null) {
                    numberDeserializer.deserialize(parser, context, intoValue)
                } else if (context.hasSomeOfFeatures(FEATURE_MASK_INT_COERCIONS)) {
                    coerceIntegral(parser, context)
                } else {
                    parser.numberValue
                }
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> {
                val numberDeserializer = myNumberDeserializer

                if (numberDeserializer != null) {
                    numberDeserializer.deserialize(parser, context, intoValue)
                } else {
                    deserializeFloatingPoint(parser, context)
                }
            }

            CirJsonTokenId.ID_TRUE -> {
                true
            }

            CirJsonTokenId.ID_FALSE -> {
                false
            }

            CirJsonTokenId.ID_NULL -> {
                null
            }

            else -> {
                deserialize(parser, context)
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    /**
     * Method called to map a CirJSON Array into a Kotlin value.
     */
    @Throws(CirJacksonException::class)
    protected open fun mapArray(parser: CirJsonParser, context: DeserializationContext): Any {
        if (parser.nextToken() == CirJsonToken.END_ARRAY) {
            return ArrayList<Any?>(2)
        }

        var value = deserialize(parser, context)

        if (parser.nextToken() == CirJsonToken.END_ARRAY) {
            return ArrayList<Any?>(2).apply {
                add(value)
            }
        }

        val value2 = deserialize(parser, context)

        if (parser.nextToken() == CirJsonToken.END_ARRAY) {
            return ArrayList<Any?>(2).apply {
                add(value)
                add(value2)
            }
        }

        val buffer = context.leaseObjectBuffer()
        var values = buffer.resetAndStart()
        var pointer = 0
        values[pointer++] = value
        values[pointer++] = value2
        var totalSize = 2

        do {
            value = deserialize(parser, context)
            ++totalSize

            if (pointer >= values.size) {
                values = buffer.appendCompletedChunk(values)
                pointer = 0
            }

            values[pointer++] = value
        } while (parser.nextToken() != CirJsonToken.END_ARRAY)

        val result = ArrayList<Any?>(totalSize)
        buffer.completeAndClearBuffer(values, pointer, result)
        context.returnObjectBuffer(buffer)
        return result
    }

    @Throws(CirJacksonException::class)
    protected open fun mapArray(parser: CirJsonParser, context: DeserializationContext,
            result: MutableCollection<Any?>): Any {
        while (parser.nextToken() != CirJsonToken.END_ARRAY) {
            result.add(deserialize(parser, context))
        }

        return result
    }

    /**
     * Method called to map a CirJSON Object into a Kotlin value.
     */
    @Throws(CirJacksonException::class)
    protected open fun mapObject(parser: CirJsonParser, context: DeserializationContext): Any? {
        val token = parser.currentToken()

        val key1 = when {
            token == CirJsonToken.START_OBJECT -> {
                parser.nextName()
            }

            token == CirJsonToken.PROPERTY_NAME -> {
                parser.currentName()
            }

            token != CirJsonToken.END_OBJECT -> {
                return context.handleUnexpectedToken(getValueType(context), parser)
            }

            else -> {
                null
            }
        } ?: return LinkedHashMap<Any?, Any?>(2)

        parser.nextToken()
        val value1 = deserialize(parser, context)
        val key2 = parser.nextName() ?: let {
            val result = LinkedHashMap<Any?, Any?>(2)
            result[key1] = value1
            return result
        }

        parser.nextToken()
        val value2 = deserialize(parser, context)

        var key = parser.nextName() ?: let {
            val result = LinkedHashMap<Any?, Any?>(4)
            result[key1] = value1

            return if (result.put(key2, value2) != null) {
                mapObjectWithDuplicates(parser, context, result, key1, value1, value2, null)
            } else {
                result
            }
        }

        val result = LinkedHashMap<Any?, Any?>()
        result[key1] = value1

        if (result.put(key2, value2) != null) {
            return mapObjectWithDuplicates(parser, context, result, key1, value1, value2, key)
        }

        do {
            parser.nextToken()
            val newValue = deserialize(parser, context)
            val oldValue = result.put(key, newValue)

            if (oldValue != null) {
                return mapObjectWithDuplicates(parser, context, result, key, oldValue, newValue, parser.nextName())
            }
        } while (parser.nextName()?.also { key = it } != null)

        return result
    }

    @Throws(CirJacksonException::class)
    protected open fun mapObjectWithDuplicates(parser: CirJsonParser, context: DeserializationContext,
            result: MutableMap<Any?, Any?>, key: String?, oldValue: Any?, newValue: Any?, nextKey: String?): Any {
        var realOldValue = oldValue
        var realNewValue = newValue
        var realNextKey = nextKey
        val squashDuplicated = context.isEnabled(StreamReadCapability.DUPLICATE_PROPERTIES)

        if (squashDuplicated) {
            squashDuplicates(result, key, realOldValue, realNewValue)
        }

        while (realNextKey != null) {
            parser.nextToken()
            realNewValue = deserialize(parser, context)
            realOldValue = result.put(key, realNewValue)

            if (realOldValue != null && squashDuplicated) {
                squashDuplicates(result, key, realOldValue, realNewValue)
            }

            realNextKey = parser.nextName()
        }

        return result
    }

    @Suppress("UNCHECKED_CAST")
    protected open fun squashDuplicates(result: MutableMap<Any?, Any?>, key: String?, oldValue: Any?, newValue: Any?) {
        if (oldValue is MutableList<*>) {
            (oldValue as MutableList<Any?>).add(newValue)
            result[key] = oldValue
        } else {
            result[key] = arrayListOf(oldValue, newValue)
        }
    }

    /**
     * Method called to map a CirJSON Array into a Kotlin Object array (`Array<Any?>`).
     */
    @Throws(CirJacksonException::class)
    protected open fun mapArrayToArray(parser: CirJsonParser, context: DeserializationContext): Array<Any?> {
        if (parser.nextToken() == CirJsonToken.END_ARRAY) {
            return NO_OBJECTS
        }

        val buffer = context.leaseObjectBuffer()
        var values = buffer.resetAndStart()
        var pointer = 0

        do {
            val value = deserialize(parser, context)

            if (pointer >= values.size) {
                values = buffer.appendCompletedChunk(values)
                pointer = 0
            }

            values[pointer++] = value
        } while (parser.nextToken() != CirJsonToken.END_ARRAY)

        val result = buffer.completeAndClearBuffer(values, pointer)
        context.returnObjectBuffer(buffer)
        return result
    }

    @Throws(CirJacksonException::class)
    protected open fun mapObject(parser: CirJsonParser, context: DeserializationContext,
            result: MutableMap<Any?, Any?>): Any {
        val token = if (parser.currentToken() == CirJsonToken.START_OBJECT) {
            parser.currentToken()
        } else {
            parser.nextToken()
        }

        if (token == CirJsonToken.END_OBJECT) {
            return result
        }

        var key = parser.currentName()

        do {
            parser.nextToken()
            val old = result[key]

            val new = if (old != null) {
                deserialize(parser, context, old)
            } else {
                deserialize(parser, context)
            }

            if (new !== old) {
                result[key] = new
            }
        } while (parser.nextName()?.also { key = it } != null)

        return result
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeFloatingPoint(parser: CirJsonParser, context: DeserializationContext): Any {
        val numberTypeFP = parser.numberTypeFP

        return if (numberTypeFP == CirJsonParser.NumberTypeFP.BIG_DECIMAL) {
            parser.bigDecimalValue
        } else if (!parser.isNaN && context.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)) {
            parser.bigDecimalValue
        } else if (numberTypeFP == CirJsonParser.NumberTypeFP.FLOAT32) {
            parser.floatValue
        } else {
            parser.doubleValue
        }
    }

    companion object {

        val NO_OBJECTS = emptyArray<Any?>()

    }

}