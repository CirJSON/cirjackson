package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.*
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

    override fun buildTypeSerializer(context: SerializerProvider, type: KotlinType,
            subtypes: Collection<NamedType>?): TypeSerializer? {
        TODO("Not yet implemented")
    }

    override fun buildTypeDeserializer(context: DeserializationContext, type: KotlinType,
            subtypes: Collection<NamedType>?): TypeDeserializer {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Construction, configuration
     *******************************************************************************************************************
     */

    override fun init(settings: CirJsonTypeInfo.Value?, resolver: TypeIdResolver?): StandardTypeResolverBuilder {
        TODO("Not yet implemented")
    }

    override fun withDefaultImplementation(defaultImplementation: KClass<*>?): StandardTypeResolverBuilder {
        TODO("Not yet implemented")
    }

    override fun withSettings(settings: CirJsonTypeInfo.Value?): StandardTypeResolverBuilder {
        TODO("Not yet implemented")
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
        get() = TODO("Not yet implemented")

    companion object {

        fun noTypeInfoBuilder(): StandardTypeResolverBuilder {
            return StandardTypeResolverBuilder(CirJsonTypeInfo.Id.NONE, null, null)
        }

    }

}