package org.cirjson.cirjackson.databind.serialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJacksonStandardImplementation
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.EnumNamingStrategyFactory
import org.cirjson.cirjackson.databind.serialization.standard.StandardScalarSerializer
import org.cirjson.cirjackson.databind.util.EnumValues
import kotlin.reflect.KClass

/**
 * Standard serializer used for [Enum] types.
 * 
 * Based on [StandardScalarSerializer] since the CirJSON value is scalar (String).
 * 
 * @property myValues This map contains pre-resolved values (since there are ways to customize actual String constants
 * to use) to use as serializations.
 * 
 * @property mySerializeAsIndex Flag that is set if we statically know serialization choice between index and textual
 * format (`null` if it needs to be dynamically checked).
 * 
 * @property myValuesByEnumNaming Map with key as converted property class defined implementation of
 * [EnumNamingStrategy] and with value as Enum names collected using [Enum.name].
 * 
 * @property myValuesByToString Map that contains pre-resolved values for [Enum.toString] to use for serialization,
 * while respecting [org.cirjson.cirjackson.annotations.CirJsonProperty] and
 * [org.cirjson.cirjackson.databind.configuration.EnumFeature.WRITE_ENUMS_TO_LOWERCASE].
 */
@CirJacksonStandardImplementation
open class EnumSerializer(protected val myValues: EnumValues, protected val mySerializeAsIndex: Boolean?,
        protected val myValuesByEnumNaming: EnumValues?, protected val myValuesByToString: EnumValues) :
        StandardScalarSerializer<Enum<*>>(myValues.enumClass) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * To support some level of per-property configuration, we will need to make things contextual. We are limited to
     * "textual vs index" choice here, however.
     */
    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        val type = handledType()!!
        val format = findFormatOverrides(provider, property, type)
        val serializeAsIndex = isShapeWrittenUsingIndex(type, format, false, mySerializeAsIndex)

        if (serializeAsIndex == mySerializeAsIndex) {
            return this
        }

        return EnumSerializer(myValues, serializeAsIndex, myValuesByEnumNaming, myValuesByToString)
    }

    /*
     *******************************************************************************************************************
     * Extended API for CirJackson databind core
     *******************************************************************************************************************
     */

    open val enumValues: EnumValues
        get() = myValues

    /*
     *******************************************************************************************************************
     * Actual serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(value: Enum<*>, generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (myValuesByEnumNaming != null) {
            generator.writeString(myValuesByEnumNaming.serializedValueFor(value))
        } else if (serializeAsIndex(serializers)) {
            generator.writeNumber(value.ordinal)
        } else if (serializers.isEnabled(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)) {
            generator.writeString(myValuesByToString.serializedValueFor(value))
        } else {
            generator.writeString(myValues.serializedValueFor(value))
        }
    }

    /*
     *******************************************************************************************************************
     * Schema support
     *******************************************************************************************************************
     */

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        val context = visitor.provider

        if (serializeAsIndex(context)) {
            visitIntFormat(visitor, typeHint, CirJsonParser.NumberType.INT)
            return
        }

        val stringVisitor = visitor.expectStringFormat(typeHint) ?: return

        val enums = LinkedHashSet<String>()

        if (context?.isEnabled(SerializationFeature.WRITE_ENUMS_USING_TO_STRING) ?: false) {
            for (value in myValuesByToString.values()) {
                enums.add(value.value)
            }
        } else {
            for (value in myValues.values()) {
                enums.add(value.value)
            }
        }

        stringVisitor.enumTypes(enums)
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    protected fun serializeAsIndex(context: SerializerProvider?): Boolean {
        return mySerializeAsIndex ?: context!!.isEnabled(SerializationFeature.WRITE_ENUMS_USING_INDEX)
    }

    companion object {

        /*
         ***************************************************************************************************************
         * Lifecycle
         ***************************************************************************************************************
         */

        /**
         * Factory method used by [org.cirjson.cirjackson.databind.serialization.BasicSerializerFactory]
         * for constructing serializer instance of Enum types.
         */
        fun construct(enumClass: KClass<*>, config: SerializationConfig, beanDescription: BeanDescription,
                format: CirJsonFormat.Value?): EnumSerializer {
            val values = EnumValues.constructFromName(config, beanDescription.classInfo)
            val valuesByEnumNaming = constructEnumNamingStrategyValues(config, beanDescription.classInfo)
            val valuesByToString = EnumValues.constructFromToString(config, beanDescription.classInfo)
            val serializeAsIndex = isShapeWrittenUsingIndex(enumClass, format, true, null)
            return EnumSerializer(values, serializeAsIndex, valuesByEnumNaming, valuesByToString)
        }

        /*
         ***************************************************************************************************************
         * Helper methods
         ***************************************************************************************************************
         */

        /**
         * Helper method called to check whether serialization should be done using index (number) or not.
         */
        fun isShapeWrittenUsingIndex(enumClass: KClass<*>, format: CirJsonFormat.Value?, fromClass: Boolean,
                defaultValue: Boolean?): Boolean? {
            val shape = format?.shape ?: return defaultValue

            return when (shape) {
                CirJsonFormat.Shape.ANY, CirJsonFormat.Shape.SCALAR -> defaultValue

                CirJsonFormat.Shape.STRING, CirJsonFormat.Shape.NATURAL -> false

                CirJsonFormat.Shape.ARRAY -> true

                else -> {
                    if (shape.isNumeric) {
                        true
                    } else {
                        throw IllegalArgumentException(
                                "Unsupported serialization shape ($shape) for Enum ${enumClass.qualifiedName}, not supported as ${if (fromClass) "class" else "property"} annotation")
                    }
                }
            }
        }

        /**
         * Factory method used to resolve an instance of [EnumValues] with [EnumNamingStrategy] applied for the target
         * class.
         */
        fun constructEnumNamingStrategyValues(config: SerializationConfig,
                annotatedClass: AnnotatedClass): EnumValues? {
            val namingDefinition = config.annotationIntrospector!!.findEnumNamingStrategy(config, annotatedClass)
            val enumNamingStrategy = EnumNamingStrategyFactory.createEnumNamingStrategyInstance(namingDefinition,
                    config.canOverrideAccessModifiers()) ?: return null
            return EnumValues.constructUsingEnumNamingStrategy(config, annotatedClass, enumNamingStrategy)
        }

    }

}