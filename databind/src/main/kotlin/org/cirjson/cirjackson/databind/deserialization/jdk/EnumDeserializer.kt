package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.configuration.CoercionAction
import org.cirjson.cirjackson.databind.configuration.CoercionInputShape
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.standard.StandardScalarDeserializer
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.CompactStringObjectMap
import org.cirjson.cirjackson.databind.util.EnumResolver
import org.cirjson.cirjackson.databind.util.checkAndFixAccess
import kotlin.reflect.KClass

/**
 * Deserializer class that can deserialize instances of specified Enum class from Strings and Ints.
 */
@CirJacksonStandardImplementation
open class EnumDeserializer : StandardScalarDeserializer<Any> {

    protected val myEnumsByIndex: Array<Any>

    protected val myEnumDefaultValue: Enum<*>?

    protected val myLookupByName: CompactStringObjectMap

    /**
     * Alternatively, we may need a different lookup object if "use toString" is defined.
     */
    protected val myLookupByToString: CompactStringObjectMap?

    protected val myCaseInsensitive: Boolean?

    private var myUseDefaultValueForUnknownEnum: Boolean?

    private var myUseNullForUnknownEnum: Boolean?

    /**
     * Marker flag for cases where we expect actual integral value for Enum, based on `@CirJsonValue` (and equivalent)
     * annotated accessor.
     */
    protected val myIsFromIntValue: Boolean

    /**
     * Look up map with **key** as `Enum.name` converted by [EnumNamingStrategy.convertEnumToExternalName] and **value**
     * as Enums.
     */
    protected val myLookupByEnumNaming: CompactStringObjectMap?

    constructor(byNameResolver: EnumResolver, caseInsensitive: Boolean, byEnumNamingResolver: EnumResolver?,
            toStringResolver: EnumResolver?) : super(byNameResolver.enumClass) {
        myLookupByName = byNameResolver.constructLookup()
        myEnumsByIndex = arrayOf(*byNameResolver.rawEnums)
        myEnumDefaultValue = byNameResolver.defaultValue
        myCaseInsensitive = caseInsensitive
        myIsFromIntValue = byNameResolver.isFromIntValue
        myLookupByEnumNaming = byEnumNamingResolver?.constructLookup()
        myLookupByToString = toStringResolver?.constructLookup()
        myUseDefaultValueForUnknownEnum = null
        myUseNullForUnknownEnum = null
    }

    protected constructor(base: EnumDeserializer, caseInsensitive: Boolean?, useDefaultValueForUnknownEnum: Boolean?,
            useNullForUnknownEnum: Boolean?) : super(base) {
        myLookupByName = base.myLookupByName
        myEnumsByIndex = base.myEnumsByIndex
        myEnumDefaultValue = base.myEnumDefaultValue
        myCaseInsensitive = caseInsensitive
        myIsFromIntValue = base.myIsFromIntValue
        myLookupByEnumNaming = base.myLookupByEnumNaming
        myLookupByToString = base.myLookupByToString
        myUseDefaultValueForUnknownEnum = useDefaultValueForUnknownEnum
        myUseNullForUnknownEnum = useNullForUnknownEnum
    }

    open fun withResolved(caseInsensitive: Boolean?, useDefaultValueForUnknownEnum: Boolean?,
            useNullForUnknownEnum: Boolean?): EnumDeserializer {
        if (myCaseInsensitive == caseInsensitive && myUseDefaultValueForUnknownEnum == useDefaultValueForUnknownEnum && myUseNullForUnknownEnum == useNullForUnknownEnum) {
            return this
        }

        return EnumDeserializer(this, caseInsensitive, useDefaultValueForUnknownEnum, useNullForUnknownEnum)
    }

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val caseInsensitive = findFormatFeature(context, property, handledType(),
                CirJsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES) ?: myCaseInsensitive
        val useDefaultValueForUnknownEnum = findFormatFeature(context, property, handledType(),
                CirJsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE) ?: myUseDefaultValueForUnknownEnum
        val useNullForUnknownEnum = findFormatFeature(context, property, handledType(),
                CirJsonFormat.Feature.READ_UNKNOWN_ENUM_VALUES_AS_NULL) ?: myUseNullForUnknownEnum
        return withResolved(caseInsensitive, useDefaultValueForUnknownEnum, useNullForUnknownEnum)
    }

    /*
     *******************************************************************************************************************
     * Default ValueDeserializer implementation
     *******************************************************************************************************************
     */

    /**
     * Because of costs associated with constructing Enum resolvers, let's cache instances by default.
     */
    override val isCacheable: Boolean
        get() = true

    override fun logicalType(): LogicalType {
        return LogicalType.ENUM
    }

    override fun getEmptyValue(context: DeserializationContext): Any? {
        return myEnumDefaultValue
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        return if (parser.hasToken(CirJsonToken.VALUE_STRING)) {
            fromString(parser, context, parser.text!!)
        } else if (parser.hasToken(CirJsonToken.VALUE_NUMBER_INT)) {
            if (myIsFromIntValue) {
                fromString(parser, context, parser.text!!)
            } else {
                fromInteger(parser, context, parser.intValue)
            }
        } else if (parser.isExpectedStartObjectToken) {
            fromString(parser, context, context.extractScalarFromObject(parser, this, myValueClass))
        } else {
            deserializeOther(parser, context)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun fromString(parser: CirJsonParser, context: DeserializationContext, text: String): Any? {
        val lookup = resolveCurrentLookup(context)
        return lookup.find(text) ?: let {
            val trimmed = text.trim()
            lookup.takeUnless { trimmed == text }?.find(trimmed) ?: return deserializeAltString(context, lookup,
                    trimmed)
        }
    }

    private fun resolveCurrentLookup(context: DeserializationContext): CompactStringObjectMap {
        return myLookupByEnumNaming ?: if (context.isEnabled(DeserializationFeature.READ_ENUMS_USING_TO_STRING)) {
            myLookupByToString!!
        } else {
            myLookupByName
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun fromInteger(parser: CirJsonParser, context: DeserializationContext, index: Int): Any? {
        val action = context.findCoercionAction(logicalType(), handledType(), CoercionInputShape.INTEGER)

        if (action == CoercionAction.FAIL) {
            if (context.isEnabled(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)) {
                return context.handleWeirdNumberValue(enumClass(), index,
                        "not allowed to deserialize Enum value out of number: disable DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS to allow")
            }

            checkCoercionFail(context, action, handledType(), index, "Integer value ($index)")
        }

        return if (action == CoercionAction.AS_NULL) {
            null
        } else if (action == CoercionAction.AS_EMPTY) {
            getEmptyValue(context)
        } else {
            if (index in myEnumsByIndex.indices) {
                myEnumsByIndex[index]
            } else if (useDefaultValueForUnknownEnum(context)) {
                myEnumDefaultValue
            } else if (useNullForUnknownEnum(context)) {
                null
            } else {
                context.handleWeirdNumberValue(enumClass(), index,
                        "index value outside legal index range [0..${myEnumsByIndex.lastIndex}]")
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Internal helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    private fun deserializeAltString(context: DeserializationContext, lookup: CompactStringObjectMap,
            originalName: String): Any? {
        val name = originalName.trim()

        if (name.isEmpty()) {
            if (useDefaultValueForUnknownEnum(context)) {
                return myEnumDefaultValue
            } else if (useNullForUnknownEnum(context)) {
                return null
            }

            val action = if (originalName.isEmpty()) {
                val action = findCoercionFromEmptyString(context)
                checkCoercionFail(context, action, handledType(), originalName, "empty String (\"\")")
            } else {
                val action = findCoercionFromBlankString(context)
                checkCoercionFail(context, action, handledType(), originalName, "blank String (all whitespace)")
            }

            return when (action) {
                CoercionAction.AS_EMPTY, CoercionAction.TRY_CONVERT -> getEmptyValue(context)
                else -> null
            }
        }

        if (!context.isEnabled(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS) && !myIsFromIntValue) {
            val char = name[0]

            if (char in '0'..'9') {
                if (char != '0' || name.length == 1) {
                    try {
                        val index = name.toInt()

                        if (!context.isEnabled(MapperFeature.ALLOW_COERCION_OF_SCALARS)) {
                            return context.handleWeirdStringValue(enumClass(), name,
                                    "value looks like quoted Enum index, but `MapperFeature.ALLOW_COERCION_OF_SCALARS` prevents use")
                        } else if (index in myEnumsByIndex.indices) {
                            return myEnumsByIndex[index]
                        }
                    } catch (_: NumberFormatException) {
                    }
                }
            }
        }

        return if (useDefaultValueForUnknownEnum(context)) {
            myEnumDefaultValue
        } else if (useNullForUnknownEnum(context)) {
            null
        } else {
            context.handleWeirdStringValue(enumClass(), name,
                    "not one of the values accepted for Enum class: ${lookup.keys()}")
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun deserializeOther(parser: CirJsonParser, context: DeserializationContext): Any? {
        return if (parser.hasToken(CirJsonToken.START_ARRAY)) {
            deserializeFromArray(parser, context)
        } else {
            context.handleUnexpectedToken(getValueType(context), parser)
        }
    }

    protected open fun enumClass(): KClass<*> {
        return handledType()
    }

    protected open fun useNullForUnknownEnum(context: DeserializationContext): Boolean {
        return myUseNullForUnknownEnum ?: context.isEnabled(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
    }

    protected open fun useDefaultValueForUnknownEnum(context: DeserializationContext): Boolean {
        myEnumDefaultValue ?: return false
        return myUseDefaultValueForUnknownEnum ?: context.isEnabled(
                DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)
    }

    companion object {

        /**
         * Factory method used when Enum instances are to be deserialized using a creator (static factory method)
         *
         * @return Deserializer based on given factory method
         */
        fun deserializerForCreator(config: DeserializationConfig, enumClass: KClass<*>, factory: AnnotatedMethod,
                valueInstantiator: ValueInstantiator?,
                creatorProperties: Array<SettableBeanProperty>): ValueDeserializer<*> {
            if (config.canOverrideAccessModifiers()) {
                factory.member.checkAndFixAccess(config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
            }

            return FactoryBasedEnumDeserializer(enumClass, factory, factory.getParameterType(0)!!, valueInstantiator,
                    creatorProperties)
        }

        /**
         * Factory method used when Enum instances are to be deserialized using a zero-/no-args factory method
         *
         * @return Deserializer based on given no-args factory method
         */
        fun deserializerForNoArgsCreator(config: DeserializationConfig, enumClass: KClass<*>,
                factory: AnnotatedMethod): ValueDeserializer<*> {
            if (config.canOverrideAccessModifiers()) {
                factory.member.checkAndFixAccess(config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
            }

            return FactoryBasedEnumDeserializer(enumClass, factory)
        }

    }

}