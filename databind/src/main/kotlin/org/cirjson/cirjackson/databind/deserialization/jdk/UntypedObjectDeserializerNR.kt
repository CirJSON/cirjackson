package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.DeserializationFeature
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.standard.StandardDeserializer
import org.cirjson.cirjackson.databind.type.LogicalType

@CirJacksonStandardImplementation
internal class UntypedObjectDeserializerNR private constructor(private val myNonMerging: Boolean) :
        StandardDeserializer<Any>(Any::class) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor() : this(false)

    override fun logicalType(): LogicalType {
        return LogicalType.UNTYPED
    }

    override fun supportsUpdate(config: DeserializationConfig): Boolean? {
        return false.takeIf { myNonMerging }
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_START_OBJECT -> deserializeNR(parser, context,
                    Scope.rootObjectScope(context.isEnabled(StreamReadCapability.DUPLICATE_PROPERTIES)))

            CirJsonTokenId.ID_END_OBJECT -> Scope.emptyMap()

            CirJsonTokenId.ID_PROPERTY_NAME -> deserializeObjectAtName(parser, context)

            CirJsonTokenId.ID_START_ARRAY -> deserializeNR(parser, context, Scope.rootArrayScope())

            CirJsonTokenId.ID_STRING -> parser.text

            CirJsonTokenId.ID_NUMBER_INT -> if (context.hasSomeOfFeatures(FEATURE_MASK_INT_COERCIONS)) {
                coerceIntegral(parser, context)
            } else {
                parser.numberValue
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> deserializeFloatingPoint(parser, context)

            CirJsonTokenId.ID_TRUE -> true

            CirJsonTokenId.ID_FALSE -> false

            CirJsonTokenId.ID_NULL -> null

            CirJsonTokenId.ID_EMBEDDED_OBJECT -> parser.embeddedObject

            else -> context.handleUnexpectedToken(getValueType(context), parser)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_START_ARRAY, CirJsonTokenId.ID_START_OBJECT, CirJsonTokenId.ID_PROPERTY_NAME -> typeDeserializer.deserializeTypedFromAny(
                    parser, context)

            else -> deserializeAnyScalar(parser, context, parser.currentTokenId())
        }
    }

    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext, intoValue: Any): Any? {
        if (myNonMerging) {
            return deserialize(parser, context)
        }

        return when (val id = parser.currentTokenId()) {
            CirJsonTokenId.ID_END_OBJECT, CirJsonTokenId.ID_END_ARRAY -> {
                intoValue
            }

            CirJsonTokenId.ID_START_OBJECT, CirJsonTokenId.ID_PROPERTY_NAME -> {
                if (id == CirJsonTokenId.ID_START_OBJECT) {
                    val token = parser.nextToken()

                    if (token == CirJsonToken.END_OBJECT) {
                        return intoValue
                    }
                }

                if (intoValue !is MutableMap<*, *>) {
                    return deserialize(parser, context)
                }

                val map = intoValue as MutableMap<Any?, Any?>
                var key = parser.currentName()

                do {
                    parser.nextToken()
                    val old = map[key]

                    val new = if (old != null) {
                        deserialize(parser, context, old)
                    } else {
                        deserialize(parser, context)
                    }

                    if (new !== old) {
                        map[key] = new
                    }
                } while (parser.nextName()?.also { key = it } != null)

                intoValue
            }

            CirJsonTokenId.ID_START_ARRAY -> {
                val token = parser.nextToken()

                if (token == CirJsonToken.END_ARRAY) {
                    return intoValue
                }

                if (intoValue !is MutableCollection<*>) {
                    return deserialize(parser, context)
                }

                val collection = intoValue as MutableCollection<Any?>

                do {
                    collection.add(deserialize(parser, context))
                } while (parser.nextToken() != CirJsonToken.END_ARRAY)

                intoValue
            }

            else -> {
                deserialize(parser, context)
            }
        }
    }

    @Throws(CirJacksonException::class)
    private fun deserializeObjectAtName(parser: CirJsonParser, context: DeserializationContext): Any? {
        val rootObject = Scope.rootObjectScope(context.isEnabled(StreamReadCapability.DUPLICATE_PROPERTIES))
        var key = parser.currentName()

        while (key != null) {
            val token = parser.nextToken() ?: CirJsonToken.NOT_AVAILABLE

            val value = when (token) {
                CirJsonToken.START_OBJECT -> deserializeNR(parser, context, rootObject.childObject())
                CirJsonToken.END_OBJECT -> return rootObject.finishRootObject()
                CirJsonToken.START_ARRAY -> deserializeNR(parser, context, rootObject.childArray())
                else -> deserializeAnyScalar(parser, context, token.id)
            }

            rootObject.putValue(key, value)
            key = parser.nextName()
        }

        return rootObject.finishRootObject()
    }

    @Throws(CirJacksonException::class)
    private fun deserializeNR(parser: CirJsonParser, context: DeserializationContext, rootScope: Scope): Any? {
        val intCoercions = context.hasSomeOfFeatures(FEATURE_MASK_INT_COERCIONS)
        val useArray = context.isEnabled(DeserializationFeature.USE_JAVA_ARRAY_FOR_CIRJSON_ARRAY)

        var currentScope = rootScope

        outerLoop@ while (true) {
            if (currentScope.isObject) {
                var propertyName = ""

                while (parser.nextName()?.also { propertyName = it } != null) {
                    val token = parser.nextToken() ?: CirJsonToken.NOT_AVAILABLE

                    val value = when (token) {
                        CirJsonToken.START_OBJECT -> {
                            currentScope = currentScope.childObject(propertyName)
                            continue
                        }

                        CirJsonToken.START_ARRAY -> {
                            currentScope = currentScope.childArray(propertyName)
                            continue@outerLoop
                        }

                        CirJsonToken.VALUE_STRING -> {
                            parser.text
                        }

                        CirJsonToken.VALUE_NUMBER_INT -> {
                            if (intCoercions) {
                                coerceIntegral(parser, context)
                            } else {
                                parser.numberValue
                            }
                        }

                        CirJsonToken.VALUE_NUMBER_FLOAT -> {
                            deserializeFloatingPoint(parser, context)
                        }

                        CirJsonToken.VALUE_TRUE -> {
                            true
                        }

                        CirJsonToken.VALUE_FALSE -> {
                            false
                        }

                        CirJsonToken.VALUE_NULL -> {
                            null
                        }

                        CirJsonToken.VALUE_EMBEDDED_OBJECT -> {
                            parser.embeddedObject
                        }

                        else -> {
                            return context.handleUnexpectedToken(getValueType(context), parser)
                        }
                    }

                    currentScope.putValue(propertyName, value)
                }

                if (currentScope === rootScope) {
                    return currentScope.finishRootObject()
                }

                currentScope = currentScope.finishBranchObject()
            } else {
                while (true) {
                    val token = parser.nextToken() ?: CirJsonToken.NOT_AVAILABLE

                    val value = when (token) {
                        CirJsonToken.START_OBJECT -> {
                            currentScope = currentScope.childObject()
                            continue@outerLoop
                        }

                        CirJsonToken.START_ARRAY -> {
                            currentScope = currentScope.childArray()
                            continue@outerLoop
                        }

                        CirJsonToken.END_ARRAY -> {
                            if (currentScope === rootScope) {
                                return currentScope.finishRootArray(useArray)
                            }

                            currentScope = currentScope.finishBranchArray(useArray)
                            break
                        }

                        CirJsonToken.VALUE_STRING -> {
                            parser.text
                        }

                        CirJsonToken.VALUE_NUMBER_INT -> {
                            if (intCoercions) {
                                coerceIntegral(parser, context)
                            } else {
                                parser.numberValue
                            }
                        }

                        CirJsonToken.VALUE_NUMBER_FLOAT -> {
                            deserializeFloatingPoint(parser, context)
                        }

                        CirJsonToken.VALUE_TRUE -> {
                            true
                        }

                        CirJsonToken.VALUE_FALSE -> {
                            false
                        }

                        CirJsonToken.VALUE_NULL -> {
                            null
                        }

                        CirJsonToken.VALUE_EMBEDDED_OBJECT -> {
                            parser.embeddedObject
                        }

                        else -> {
                            return context.handleUnexpectedToken(getValueType(context), parser)
                        }
                    }

                    currentScope.addValue(value)
                }
            }
        }
    }

    @Throws(CirJacksonException::class)
    private fun deserializeAnyScalar(parser: CirJsonParser, context: DeserializationContext, tokenType: Int): Any? {
        return when (tokenType) {
            CirJsonTokenId.ID_STRING -> parser.text

            CirJsonTokenId.ID_NUMBER_INT -> if (context.isEnabled(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)) {
                parser.bigIntegerValue
            } else {
                parser.numberValue
            }

            CirJsonTokenId.ID_NUMBER_FLOAT -> deserializeFloatingPoint(parser, context)

            CirJsonTokenId.ID_TRUE -> true

            CirJsonTokenId.ID_FALSE -> false

            CirJsonTokenId.ID_NULL -> null

            CirJsonTokenId.ID_EMBEDDED_OBJECT -> parser.embeddedObject

            else -> context.handleUnexpectedToken(getValueType(context), parser)
        }
    }

    @Throws(CirJacksonException::class)
    private fun deserializeFloatingPoint(parser: CirJsonParser, context: DeserializationContext): Any {
        val numberType = parser.numberTypeFP

        return if (numberType == CirJsonParser.NumberTypeFP.BIG_DECIMAL) {
            parser.bigDecimalValue
        } else if (!parser.isNaN && context.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)) {
            parser.bigDecimalValue
        } else if (numberType == CirJsonParser.NumberTypeFP.FLOAT32) {
            parser.floatValue
        } else {
            parser.doubleValue
        }
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Helper class used for building Maps and Lists/Arrays.
     */
    private class Scope {

        private val myParent: Scope?

        val isObject: Boolean

        private val mySquashDuplicates: Boolean

        private var myDeferredKey: String? = null

        private var myMap: MutableMap<String, Any?>? = null

        private var myList: MutableList<Any?>? = null

        /*
         ***************************************************************************************************************
         * Lifecycle
         ***************************************************************************************************************
         */

        private constructor(parent: Scope?) {
            myParent = parent
            isObject = false
            mySquashDuplicates = false
        }

        private constructor(parent: Scope?, isObject: Boolean, squashDuplicates: Boolean) {
            myParent = parent
            this.isObject = isObject
            mySquashDuplicates = squashDuplicates
        }

        fun childObject(): Scope {
            return Scope(this, true, mySquashDuplicates)
        }

        fun childObject(deferredKey: String): Scope {
            myDeferredKey = deferredKey
            return Scope(this, true, mySquashDuplicates)
        }

        fun childArray(): Scope {
            return Scope(this)
        }

        fun childArray(deferredKey: String): Scope {
            myDeferredKey = deferredKey
            return Scope(this)
        }

        /*
         ***************************************************************************************************************
         * Value construction
         ***************************************************************************************************************
         */

        fun putValue(key: String, value: Any?) {
            if (mySquashDuplicates) {
                putValueHandleDuplicates(key, value)
                return
            }

            (myMap ?: LinkedHashMap<String, Any?>().also { myMap = it })[key] = value
        }

        fun putDeferredValue(value: Any?): Scope {
            val key = myDeferredKey!!
            myDeferredKey = null

            putValue(key, value)
            return this
        }

        fun addValue(value: Any?) {
            (myList ?: ArrayList<Any?>().also { myList = it }).add(value)
        }

        fun finishRootObject(): Any {
            return myMap ?: emptyMap()
        }

        fun finishBranchObject(): Scope {
            val map = myMap
            val value = map?.also { myMap = null } ?: LinkedHashMap()

            return if (myParent!!.isObject) {
                myParent.putDeferredValue(value)
            } else {
                myParent.also { it.addValue(value) }
            }
        }

        fun finishRootArray(asArray: Boolean): Any {
            val list = myList

            return if (list == null) {
                if (asArray) {
                    NO_OBJECTS
                } else {
                    emptyList()
                }
            } else if (asArray) {
                list.toTypedArray()
            } else {
                list
            }
        }

        fun finishBranchArray(asArray: Boolean): Scope {
            val list = myList

            val value = if (list == null) {
                if (asArray) {
                    NO_OBJECTS
                } else {
                    emptyList()
                }
            } else {
                myList = null

                if (asArray) {
                    list.toTypedArray()
                } else {
                    list
                }
            }

            return if (myParent!!.isObject) {
                myParent.putDeferredValue(value)
            } else {
                myParent.also { it.addValue(value) }
            }
        }

        /*
         ***************************************************************************************************************
         * Helper methods
         ***************************************************************************************************************
         */

        /**
         * Helper method that deals with merging of duplicates, when that is expected. Only used with formats that
         * expose seeming "duplicates" in Object values: most notable this is the case for XML.
         */
        @Suppress("UNCHECKED_CAST")
        private fun putValueHandleDuplicates(key: String, newValue: Any?) {
            val map = myMap

            if (map == null) {
                myMap = LinkedHashMap<String, Any?>().apply { put(key, newValue) }
                return
            }

            val oldValue = map.put(key, newValue) ?: return

            if (oldValue is MutableList<*>) {
                (oldValue as MutableList<Any?>).add(newValue)
                map[key] = oldValue
            } else {
                map[key] = arrayListOf(oldValue, newValue)
            }
        }

        companion object {

            fun rootObjectScope(squashDuplicates: Boolean): Scope {
                return Scope(null, true, squashDuplicates)
            }

            fun rootArrayScope(): Scope {
                return Scope(null)
            }

            fun emptyMap(): MutableMap<String, Any?> {
                return LinkedHashMap(2)
            }

            fun emptyList(): MutableList<Any?> {
                return ArrayList(2)
            }

        }

    }

    companion object {

        private val NO_OBJECTS = emptyArray<Any?>()

        val STANDARD = UntypedObjectDeserializerNR()

        fun instance(nonMerging: Boolean): UntypedObjectDeserializerNR {
            return if (nonMerging) {
                UntypedObjectDeserializerNR(true)
            } else {
                STANDARD
            }
        }

    }

}