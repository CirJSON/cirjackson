package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.TreeNode
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.cirjsontype.PolymorphicTypeValidator
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import org.cirjson.cirjackson.databind.util.verifyMustOverride
import kotlin.reflect.KClass

/**
 * Customized [TypeResolverBuilder][org.cirjson.cirjackson.databind.cirjsontype.TypeResolverBuilder] that provides type
 * resolver builders used with so-called "default typing" (see
 * [org.cirjson.cirjackson.databind.configuration.MapperBuilder.activateDefaultTyping] for details).
 *
 * Type resolver construction is based on configuration: implementation takes care of only providing builders in cases
 * where type information should be applied. This is important since build calls may be sent for any and all types, and
 * type information should NOT be applied to all of them.
 */
open class DefaultTypeResolverBuilder : StandardTypeResolverBuilder {

    /**
     * Validator to use for checking that only valid subtypes are accepted from incoming content.
     */
    protected val mySubtypeValidator: PolymorphicTypeValidator

    /**
     * Definition of what types is this default typer valid for.
     */
    protected val myAppliesFor: DefaultTyping

    constructor(subtypeValidator: PolymorphicTypeValidator, typing: DefaultTyping,
            includeAs: CirJsonTypeInfo.As?) : super(CirJsonTypeInfo.Id.CLASS, includeAs,
            CirJsonTypeInfo.Id.CLASS.defaultPropertyName) {
        mySubtypeValidator = subtypeValidator
        myAppliesFor = typing
    }

    constructor(subtypeValidator: PolymorphicTypeValidator, typing: DefaultTyping, propertyName: String?) : super(
            CirJsonTypeInfo.Id.CLASS, CirJsonTypeInfo.As.PROPERTY, propertyName) {
        mySubtypeValidator = subtypeValidator
        myAppliesFor = typing
    }

    constructor(subtypeValidator: PolymorphicTypeValidator, typing: DefaultTyping, includeAs: CirJsonTypeInfo.As?,
            idType: CirJsonTypeInfo.Id, propertyName: String?) : super(idType, includeAs,
            propertyName ?: idType.defaultPropertyName) {
        mySubtypeValidator = subtypeValidator
        myAppliesFor = typing
    }

    protected constructor(base: DefaultTypeResolverBuilder, defaultImplementation: KClass<*>?) : super(base,
            defaultImplementation) {
        mySubtypeValidator = base.mySubtypeValidator
        myAppliesFor = base.myAppliesFor
    }

    override fun withDefaultImplementation(defaultImplementation: KClass<*>?): DefaultTypeResolverBuilder {
        if (myDefaultImplementation === defaultImplementation) {
            return this
        }

        verifyMustOverride(DefaultTypeResolverBuilder::class, this, "withDefaultImplementation")

        return DefaultTypeResolverBuilder(this, defaultImplementation)
    }

    override fun subTypeValidator(context: DatabindContext): PolymorphicTypeValidator {
        return mySubtypeValidator
    }

    override fun buildTypeSerializer(context: SerializerProvider, baseType: KotlinType,
            subtypes: Collection<NamedType>?): TypeSerializer? {
        if (!useForType(baseType)) {
            return null
        }

        return super.buildTypeSerializer(context, baseType, subtypes)
    }

    override fun buildTypeDeserializer(context: DeserializationContext, baseType: KotlinType,
            subtypes: Collection<NamedType>?): TypeDeserializer? {
        if (!useForType(baseType)) {
            return null
        }

        return super.buildTypeDeserializer(context, baseType, subtypes)
    }

    open fun typeIdVisibility(isVisible: Boolean): DefaultTypeResolverBuilder {
        myTypeIdVisible = isVisible
        return this
    }

    /**
     * Method called to check if the default type handler should be used for given type. Note: "natural types" (String,
     * Boolean, Integer, Double) will never use typing; that is both due to them being concrete and final, and since
     * actual serializers and deserializers will also ignore any attempts to enforce typing.
     */
    open fun useForType(type: KotlinType): Boolean {
        var realType = type

        if (type.isPrimitive) {
            return false
        }

        return when (myAppliesFor) {
            DefaultTyping.NON_CONCRETE_AND_ARRAYS, DefaultTyping.OBJECT_AND_NON_CONCRETE -> {
                if (myAppliesFor == DefaultTyping.NON_CONCRETE_AND_ARRAYS) {
                    realType = unwrapArrayType(realType)
                }

                realType = unwrapReferenceType(realType)
                realType.isJavaLangObject ||
                        !realType.isConcrete && !TreeNode::class.isAssignableFrom(realType.rawClass)
            }

            DefaultTyping.NON_FINAL -> {
                realType = unwrapArrayType(realType)
                realType = unwrapReferenceType(realType)
                !realType.isFinal && !TreeNode::class.isAssignableFrom(realType.rawClass)
            }

            DefaultTyping.NON_FINAL_AND_ENUMS -> {
                realType = unwrapArrayType(realType)
                realType = unwrapReferenceType(realType)
                !realType.isFinal && !TreeNode::class.isAssignableFrom(realType.rawClass) || realType.isEnumType
            }

            DefaultTyping.OBJECT -> {
                realType.isJavaLangObject
            }
        }
    }

    protected open fun unwrapArrayType(type: KotlinType): KotlinType {
        var realType = type

        while (realType.isArrayType) {
            realType = realType.contentType!!
        }

        return realType
    }

    protected open fun unwrapReferenceType(type: KotlinType): KotlinType {
        var realType = type

        while (realType.isReferenceType) {
            realType = realType.referencedType!!
        }

        return realType
    }

}