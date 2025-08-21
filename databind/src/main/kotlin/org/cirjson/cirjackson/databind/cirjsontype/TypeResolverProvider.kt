package org.cirjson.cirjackson.databind.cirjsontype

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.implementation.StandardTypeResolverBuilder
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import kotlin.reflect.KClass

/**
 * Abstraction used for allowing construction and registration of custom [TypeResolverBuilders][TypeResolverBuilder],
 * used in turn for actual construction of [TypeSerializers][TypeSerializer] and [TypeDeserializers][TypeDeserializer]
 * for Polymorphic type handling. At this point contains both API and default implementation.
 */
open class TypeResolverProvider {

    /*
     *******************************************************************************************************************
     * Public API, for class
     *******************************************************************************************************************
     */

    /**
     * Method for checking if given class has annotations that indicate that specific type resolver is to be used for
     * handling instances of given type. This includes not only instantiating resolver builder, but also configuring it
     * based on relevant annotations (not including ones checked with a call to `findSubtypes()`
     *
     * @param baseType Base java type of value for which resolver is to be found
     *
     * @param classInfo Introspected annotation information for the class (type)
     *
     * @return Type resolver builder for given type, if one found; `null` if none
     */
    open fun findTypeSerializer(context: SerializerProvider, baseType: KotlinType,
            classInfo: AnnotatedClass): TypeSerializer? {
        val config = context.config
        val builder = findTypeResolver(config, classInfo, baseType) ?: config.getDefaultTyper(baseType) ?: return null
        val subtypes = config.subtypeResolver.collectAndResolveSubtypesByClass(config, classInfo)
        return builder.buildTypeSerializer(context, baseType, subtypes)
    }

    open fun findTypeDeserializer(context: DeserializationContext, baseType: KotlinType,
            classInfo: AnnotatedClass): TypeDeserializer? {
        val config = context.config
        var builder = findTypeResolver(config, classInfo, baseType) ?: config.getDefaultTyper(baseType) ?: return null
        val subtypes = config.subtypeResolver.collectAndResolveSubtypesByTypeId(config, classInfo)

        if (builder.defaultImplementation == null && baseType.isAbstract) {
            val defaultType = config.mapAbstractType(baseType)

            if (defaultType != null && !defaultType.hasRawClass(baseType.rawClass)) {
                builder = builder.withDefaultImplementation(defaultType.rawClass)
            }
        }

        return builder.buildTypeDeserializer(context, baseType, subtypes)
    }

    /*
     *******************************************************************************************************************
     * Public API, for property
     *******************************************************************************************************************
     */

    open fun findPropertyTypeSerializer(context: SerializerProvider, accessor: AnnotatedMember,
            baseType: KotlinType): TypeSerializer? {
        val config = context.config
        var builder: TypeResolverBuilder<*>? = null

        if (!baseType.isContainerType && !baseType.isReferenceType) {
            builder = findTypeResolver(config, accessor, baseType)
        }

        builder ?: return findTypeSerializer(context, baseType, context.introspectClassAnnotations(baseType))

        val subtypes = config.subtypeResolver.collectAndResolveSubtypesByClass(config, accessor, baseType)
        return builder.buildTypeSerializer(context, baseType, subtypes)
    }

    open fun findPropertyTypeDeserializer(context: DeserializationContext, accessor: AnnotatedMember,
            baseType: KotlinType): TypeDeserializer? {
        val config = context.config
        var builder: TypeResolverBuilder<*>? = null

        if (!baseType.isContainerType && !baseType.isReferenceType) {
            builder = findTypeResolver(config, accessor, baseType)
        }

        builder ?: return findTypeDeserializer(context, baseType, context.introspectClassAnnotations(baseType))

        val subtypes = config.subtypeResolver.collectAndResolveSubtypesByTypeId(config, accessor, baseType)

        if (builder.defaultImplementation == null && baseType.isAbstract) {
            val defaultType = config.mapAbstractType(baseType)

            if (defaultType != null && !defaultType.hasRawClass(baseType.rawClass)) {
                builder = builder.withDefaultImplementation(defaultType.rawClass)
            }
        }

        return builder.buildTypeDeserializer(context, baseType, subtypes)
    }

    open fun findPropertyContentTypeSerializer(context: SerializerProvider, accessor: AnnotatedMember,
            containerType: KotlinType): TypeSerializer? {
        val contentType = containerType.contentType ?: throw IllegalArgumentException(
                "Must call method with a container or reference type (got $containerType)")
        val config = context.config

        val builder = findTypeResolver(config, accessor, contentType) ?: return findTypeSerializer(context, contentType,
                context.introspectClassAnnotations(contentType))

        val subtypes = config.subtypeResolver.collectAndResolveSubtypesByClass(config, accessor, contentType)
        return builder.buildTypeSerializer(context, contentType, subtypes)
    }

    open fun findPropertyContentTypeDeserializer(context: DeserializationContext, accessor: AnnotatedMember,
            containerType: KotlinType): TypeDeserializer? {
        val contentType = containerType.contentType ?: throw IllegalArgumentException(
                "Must call method with a container or reference type (got $containerType)")
        val config = context.config

        var builder =
                findTypeResolver(config, accessor, contentType) ?: return findTypeDeserializer(context, contentType,
                        context.introspectClassAnnotations(contentType))

        val subtypes = config.subtypeResolver.collectAndResolveSubtypesByTypeId(config, accessor, contentType)

        if (builder.defaultImplementation == null && contentType.isAbstract) {
            val defaultType = config.mapAbstractType(contentType)

            if (defaultType != null && !defaultType.hasRawClass(contentType.rawClass)) {
                builder = builder.withDefaultImplementation(defaultType.rawClass)
            }
        }

        return builder.buildTypeDeserializer(context, contentType, subtypes)
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    protected open fun findTypeResolver(config: MapperConfig<*>, annotated: Annotated,
            baseType: KotlinType): TypeResolverBuilder<*>? {
        val annotationIntrospector = config.annotationIntrospector!!
        var typeInfo = annotationIntrospector.findPolymorphicTypeInfo(config, annotated)

        val customResolverObject = annotationIntrospector.findTypeResolverBuilder(config, annotated)

        var builder = if (customResolverObject != null) {
            if (typeInfo != null && typeInfo.idType == CirJsonTypeInfo.Id.NONE) {
                return null
            }

            if (customResolverObject is KClass<*>) {
                @Suppress("UNCHECKED_CAST")
                config.typeResolverBuilderInstance(annotated, customResolverObject as KClass<TypeResolverBuilder<*>>)
            } else {
                customResolverObject as TypeResolverBuilder<*>
            }
        } else {
            if (typeInfo == null) {
                return null
            }

            if (typeInfo.idType == CirJsonTypeInfo.Id.NONE) {
                return NO_RESOLVER
            }

            if (annotated is AnnotatedClass) {
                val inclusion = typeInfo.inclusionType

                if (inclusion == CirJsonTypeInfo.As.EXTERNAL_PROPERTY) {
                    typeInfo = typeInfo.withInclusionType(CirJsonTypeInfo.As.PROPERTY)
                }
            }

            constructStandardTypeResolverBuilder(config, typeInfo, baseType)
        }

        val customIdResolverObject = annotationIntrospector.findTypeIdResolver(config, annotated)
        var idResolver: TypeIdResolver? = null

        if (customIdResolverObject is KClass<*>) {
            @Suppress("UNCHECKED_CAST")
            idResolver = config.typeIdResolverInstance(annotated, customIdResolverObject as KClass<TypeIdResolver>)!!
            idResolver.init(baseType)
        }

        return builder!!.init(typeInfo, idResolver)
    }

    protected open fun constructStandardTypeResolverBuilder(config: MapperConfig<*>, typeInfo: CirJsonTypeInfo.Value,
            baseType: KotlinType): TypeResolverBuilder<*>? {
        return StandardTypeResolverBuilder(typeInfo)
    }

    companion object {

        val NO_RESOLVER = StandardTypeResolverBuilder.noTypeInfoBuilder()

    }

}