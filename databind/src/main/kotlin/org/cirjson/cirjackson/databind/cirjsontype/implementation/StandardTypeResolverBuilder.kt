package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.*
import org.cirjson.cirjackson.databind.introspection.AnnotatedClassResolver
import org.cirjson.cirjackson.databind.util.className
import org.cirjson.cirjackson.databind.util.verifyMustOverride
import kotlin.reflect.KClass

/**
 * Default [TypeResolverBuilder] implementation.
 */
open class StandardTypeResolverBuilder : TypeResolverBuilder<StandardTypeResolverBuilder> {

    protected var myIdType: CirJsonTypeInfo.Id

    protected var myIncludeAs: CirJsonTypeInfo.As?

    protected var myTypeProperty: String?

    /**
     * Whether type id should be exposed to deserializers or not
     */
    protected var myTypeIdVisible: Boolean = false

    /**
     *
     * Boolean value configured through [CirJsonTypeInfo.requireTypeIdForSubtypes]. If this value is not `null`, this
     * value should override the global configuration of
     * [org.cirjson.cirjackson.databind.MapperFeature.REQUIRE_TYPE_ID_FOR_SUBTYPES].
     */
    protected var myRequireTypeIdForSubtypes: Boolean? = null

    /**
     * Default class to use in case type information is not available or is broken.
     */
    protected var myDefaultImplementation: KClass<*>? = null

    protected var myCustomIdResolver: TypeIdResolver? = null

    /*
     *******************************************************************************************************************
     * Construction, initialization, actual building
     *******************************************************************************************************************
     */

    constructor(settings: CirJsonTypeInfo.Value) {
        myIdType = settings.idType
        myIncludeAs = settings.inclusionType
        myTypeProperty = propName(settings.propertyName, myIdType)
        myDefaultImplementation = settings.defaultImplementation
        myTypeIdVisible = settings.visible
        myRequireTypeIdForSubtypes = settings.requireTypeIdForSubtypes
    }

    constructor(idType: CirJsonTypeInfo.Id, idAs: CirJsonTypeInfo.As?, propName: String?) {
        myIdType = idType
        myIncludeAs = idAs
        myTypeProperty = propName(propName, myIdType)
    }

    constructor(base: StandardTypeResolverBuilder, defaultImplementation: KClass<*>?) {
        myIdType = base.myIdType
        myIncludeAs = base.myIncludeAs
        myTypeProperty = base.myTypeProperty
        myTypeIdVisible = base.myTypeIdVisible
        myCustomIdResolver = base.myCustomIdResolver

        myDefaultImplementation = defaultImplementation
        myRequireTypeIdForSubtypes = base.myRequireTypeIdForSubtypes
    }

    override fun buildTypeSerializer(context: SerializerProvider, baseType: KotlinType,
            subtypes: Collection<NamedType>?): TypeSerializer? {
        if (myIdType == CirJsonTypeInfo.Id.NONE) {
            return null
        }

        if (baseType.isPrimitive) {
            if (!allowPrimitiveTypes(context, baseType)) {
                return null
            }
        }

        if (myIdType == CirJsonTypeInfo.Id.DEDUCTION) {
            return AsDeductionTypeSerializer.INSTANCE
        }

        val idResolver = idResolver(context, baseType, subTypeValidator(context), subtypes, forSerialization = true,
                forDeserialization = false)

        return when (myIncludeAs) {
            CirJsonTypeInfo.As.WRAPPER_ARRAY -> AsArrayTypeSerializer(idResolver, null)
            CirJsonTypeInfo.As.PROPERTY -> AsPropertyTypeSerializer(idResolver, null, myTypeProperty!!)
            CirJsonTypeInfo.As.WRAPPER_OBJECT -> AsWrapperTypeSerializer(idResolver, null)
            CirJsonTypeInfo.As.EXTERNAL_PROPERTY -> AsExternalTypeSerializer(idResolver, null, myTypeProperty!!)
            CirJsonTypeInfo.As.EXISTING_PROPERTY -> AsExistingPropertyTypeSerializer(idResolver, null, myTypeProperty!!)
            else -> throw IllegalStateException(
                    "Do not know how to construct standard type serializer for inclusion type: $myIncludeAs")
        }
    }

    override fun buildTypeDeserializer(context: DeserializationContext, baseType: KotlinType,
            subtypes: Collection<NamedType>?): TypeDeserializer? {
        if (myIdType == CirJsonTypeInfo.Id.NONE) {
            return null
        }

        if (baseType.isPrimitive) {
            if (!allowPrimitiveTypes(context, baseType)) {
                return null
            }
        }

        val subtypeValidator = verifyBaseTypeValidity(context, baseType)

        val idResolver = idResolver(context, baseType, subtypeValidator, subtypes, forSerialization = false,
                forDeserialization = true)
        val defaultImplementation = defineDefaultImplementation(context, baseType)

        if (myIdType == CirJsonTypeInfo.Id.DEDUCTION) {
            return AsDeductionTypeDeserializer(context, baseType, idResolver, defaultImplementation, subtypes!!)
        }

        return when (myIncludeAs) {
            CirJsonTypeInfo.As.WRAPPER_ARRAY -> AsArrayTypeDeserializer(baseType, idResolver, myTypeProperty,
                    myTypeIdVisible, defaultImplementation)

            CirJsonTypeInfo.As.PROPERTY, CirJsonTypeInfo.As.EXISTING_PROPERTY -> AsPropertyTypeDeserializer(baseType,
                    idResolver, myTypeProperty, myTypeIdVisible, defaultImplementation, myIncludeAs,
                    strictTypeIdHandling(context, baseType))

            CirJsonTypeInfo.As.WRAPPER_OBJECT -> AsWrapperTypeDeserializer(baseType, idResolver, myTypeProperty,
                    myTypeIdVisible, defaultImplementation)

            CirJsonTypeInfo.As.EXTERNAL_PROPERTY -> AsExternalTypeDeserializer(baseType, idResolver, myTypeProperty,
                    myTypeIdVisible, defaultImplementation)

            else -> throw IllegalStateException(
                    "Do not know how to construct standard type deserializer for inclusion type: $myIncludeAs")
        }
    }

    protected open fun defineDefaultImplementation(context: DeserializationContext, baseType: KotlinType): KotlinType? {
        if (myDefaultImplementation != null) {
            if (myDefaultImplementation == Unit::class) {
                return context.typeFactory.constructType(Unit::class.java)
            }

            if (baseType.hasRawClass(myDefaultImplementation!!)) {
                return baseType
            }

            if (baseType.isTypeOrSuperTypeOf(myDefaultImplementation!!)) {
                return context.typeFactory.constructSpecializedType(baseType, myDefaultImplementation!!)
            }

            if (baseType.hasRawClass(myDefaultImplementation!!)) {
                return baseType
            }
        }

        if (context.isEnabled(MapperFeature.USE_BASE_TYPE_AS_DEFAULT_IMPL) && !baseType.isAbstract) {
            return baseType
        }

        return null
    }

    /*
     *******************************************************************************************************************
     * Construction, configuration
     *******************************************************************************************************************
     */

    override fun init(settings: CirJsonTypeInfo.Value?, resolver: TypeIdResolver?): StandardTypeResolverBuilder {
        myCustomIdResolver = resolver

        if (settings != null) {
            withSettings(settings)
        }

        return this
    }

    override fun withDefaultImplementation(defaultImplementation: KClass<*>?): StandardTypeResolverBuilder {
        if (myDefaultImplementation == defaultImplementation) {
            return this
        }

        verifyMustOverride(StandardTypeResolverBuilder::class, this, "withDefaultImplementation")

        return StandardTypeResolverBuilder(this, defaultImplementation)
    }

    override fun withSettings(settings: CirJsonTypeInfo.Value): StandardTypeResolverBuilder {
        myIdType = settings.idType
        myIncludeAs = settings.inclusionType
        myTypeProperty = propName(settings.propertyName, myIdType)
        myDefaultImplementation = settings.defaultImplementation
        myTypeIdVisible = settings.visible
        myRequireTypeIdForSubtypes = settings.requireTypeIdForSubtypes
        return this
    }

    protected open fun propName(propName: String?, idType: CirJsonTypeInfo.Id): String? {
        return propName.takeUnless { it.isNullOrEmpty() } ?: idType.defaultPropertyName
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    override val defaultImplementation: KClass<*>?
        get() = myDefaultImplementation

    open val typeProperty: String?
        get() = myTypeProperty

    open val typeIdVisible: Boolean
        get() = myTypeIdVisible

    /*
     *******************************************************************************************************************
     * Internal/subtype factory methods
     *******************************************************************************************************************
     */

    /**
     * Helper method that will either return configured custom type id resolver, or construct a standard resolver given
     * configuration.
     */
    protected open fun idResolver(context: DatabindContext, baseType: KotlinType,
            subtypeValidator: PolymorphicTypeValidator, subtypes: Collection<NamedType>?, forSerialization: Boolean,
            forDeserialization: Boolean): TypeIdResolver {
        if (myCustomIdResolver != null) {
            return myCustomIdResolver!!
        }

        return when (myIdType) {
            CirJsonTypeInfo.Id.DEDUCTION, CirJsonTypeInfo.Id.CLASS -> ClassNameIdResolver.construct(baseType,
                    subtypeValidator)

            CirJsonTypeInfo.Id.MINIMAL_CLASS -> MinimalClassNameIdResolver.construct(baseType, subtypeValidator)

            CirJsonTypeInfo.Id.SIMPLE_NAME -> SimpleNameIdResolver.construct(context.config, baseType, subtypes,
                    forSerialization, forDeserialization)

            CirJsonTypeInfo.Id.NAME -> TypeNameIdResolver.construct(context.config, baseType, subtypes,
                    forSerialization, forDeserialization)

            CirJsonTypeInfo.Id.NONE -> throw IllegalStateException("Should never get this far with 'NONE'")

            CirJsonTypeInfo.Id.CUSTOM -> throw IllegalStateException(
                    "Do not know how to construct standard type id resolver for idType: $myIdType")
        }
    }

    /**
     * Overridable helper method for determining actual validator to use when constructing type serializers and type
     * deserializers.
     *
     * Default implementation simply uses one configured and accessible using
     * [MapperConfig.polymorphicTypeValidator][org.cirjson.cirjackson.databind.configuration.MapperConfig.polymorphicTypeValidator].
     */
    open fun subTypeValidator(context: DatabindContext): PolymorphicTypeValidator {
        return context.config.polymorphicTypeValidator
    }

    /**
     * Helper method called to check that base type is valid regarding possible constraints on basetype/subtype
     * combinations allowed for polymorphic type handling. Currently, limits are verified for class name - based methods
     * only.
     */
    protected open fun verifyBaseTypeValidity(context: DatabindContext,
            baseType: KotlinType): PolymorphicTypeValidator {
        val polymorphicTypeValidator = subTypeValidator(context)

        if (myIdType != CirJsonTypeInfo.Id.CLASS && myIdType != CirJsonTypeInfo.Id.MINIMAL_CLASS) {
            return polymorphicTypeValidator
        }

        val validity = polymorphicTypeValidator.validateBaseType(context, baseType)

        if (validity == PolymorphicTypeValidator.Validity.DENIED) {
            return reportInvalidBaseType(context, baseType, polymorphicTypeValidator)
        }

        if (validity == PolymorphicTypeValidator.Validity.ALLOWED) {
            return LaissezFaireSubTypeValidator
        }

        return polymorphicTypeValidator
    }

    protected open fun reportInvalidBaseType(context: DatabindContext, baseType: KotlinType,
            polymorphicTypeValidator: PolymorphicTypeValidator): PolymorphicTypeValidator {
        return context.reportBadDefinition(baseType,
                "Configured `PolymorphicTypeValidator` (of type ${polymorphicTypeValidator.className}) denied resolution of all subtypes of base type ${baseType.rawClass.className}")
    }

    /*
     *******************************************************************************************************************
     * Overridable helper methods
     *******************************************************************************************************************
     */

    /**
     * Overridable helper method that is called to determine whether type serializers and type deserializers may be
     * created even if base type is `primitive` type. Default implementation simply returns `false` (since primitive
     * types can not be sub-classed, are never polymorphic) but custom implementations may change the logic for some
     * special cases.
     *
     * @param context Currently active context
     *
     * @param baseType Primitive base type for property being handled
     *
     * @return `true` if type (de)serializer may be created even if base type is `primitive` type; `false` if not
     */
    protected open fun allowPrimitiveTypes(context: DatabindContext, baseType: KotlinType): Boolean {
        return false
    }

    /**
     * Determines whether strict type ID handling should be used for this type or not. This will be enabled as
     * configured by [CirJsonTypeInfo.requireTypeIdForSubtypes] unless its value is
     * [org.cirjson.cirjackson.annotations.OptionalBoolean.DEFAULT]. In case the value of
     * [CirJsonTypeInfo.requireTypeIdForSubtypes] is `OptionalBoolean.DEFAULT`, this will be enabled when either the
     * type has type resolver annotations or if [MapperFeature.REQUIRE_TYPE_ID_FOR_SUBTYPES] is enabled.
     *
     * @param context Currently active context
     *
     * @param baseType the base type to check for type resolver annotations
     *
     * @return `true` if the class has type resolver annotations, or the strict handling feature is enabled, `false`
     * otherwise.
     */
    protected open fun strictTypeIdHandling(context: DatabindContext, baseType: KotlinType): Boolean {
        if (myRequireTypeIdForSubtypes != null && baseType.isConcrete) {
            return myRequireTypeIdForSubtypes!!
        }

        if (context.isEnabled(MapperFeature.REQUIRE_TYPE_ID_FOR_SUBTYPES)) {
            return true
        }

        return hasTypeResolver(context, baseType)
    }

    /**
     * Checks whether the given class has annotations indicating some type resolver is applied, for example
     * [CirJsonTypeInfo]. Only initializes `hasTypeResolver` once if its value is `null`.
     *
     * @param context Currently active context
     *
     * @param baseType the base type to check for type resolver annotations
     *
     * @return `true` if the class has type resolver annotations, `false` otherwise
     */
    protected open fun hasTypeResolver(context: DatabindContext, baseType: KotlinType): Boolean {
        val annotatedClass = AnnotatedClassResolver.resolveWithoutSuperTypes(context.config, baseType.rawClass)
        val annotationIntrospector = context.annotationIntrospector
        return annotationIntrospector!!.findPolymorphicTypeInfo(context.config, annotatedClass) != null
    }

    companion object {

        fun noTypeInfoBuilder(): StandardTypeResolverBuilder {
            return StandardTypeResolverBuilder(CirJsonTypeInfo.Id.NONE, null, null)
        }

    }

}