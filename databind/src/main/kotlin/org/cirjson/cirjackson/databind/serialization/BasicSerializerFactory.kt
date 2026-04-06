package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJsonSerialize
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.configuration.SerializerFactoryConfig
import org.cirjson.cirjackson.databind.external.OptionalHandlerFactory
import org.cirjson.cirjackson.databind.external.jdk8.*
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.BasicBeanDescription
import org.cirjson.cirjackson.databind.serialization.cirjackson.CirJacksonSerializableSerializer
import org.cirjson.cirjackson.databind.serialization.cirjackson.CirJsonValueSerializer
import org.cirjson.cirjackson.databind.serialization.jdk.*
import org.cirjson.cirjackson.databind.serialization.standard.StandardContainerSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardDelegatingSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ToEmptyObjectSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ToStringSerializer
import org.cirjson.cirjackson.databind.type.*
import org.cirjson.cirjackson.databind.util.*
import java.math.BigDecimal
import java.math.BigInteger
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.file.Path
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.DoubleStream
import java.util.stream.IntStream
import java.util.stream.LongStream
import java.util.stream.Stream
import kotlin.reflect.KClass

/**
 * Factory class that can provide serializers for standard classes, as well as custom classes that extend standard
 * classes or implement one of "well-known" interfaces (such as [Collection]).
 * 
 * Since all the serializers are eagerly instantiated, and there is no additional introspection or customizability of
 * these types, this factory is essentially stateless.
 * 
 * @constructor We will provide default constructor to allow subclassing, but make it protected so that no non-singleton
 * instances of the class will be instantiated.
 */
abstract class BasicSerializerFactory protected constructor(config: SerializerFactoryConfig?) : SerializerFactory() {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    /**
     * Configuration settings for this factory; immutable instance (just like this factory), new version created via
     * copy-constructor (fluent-style)
     */
    protected val myFactoryConfig = config ?: SerializerFactoryConfig()

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Method used for creating a new instance of this factory, but with different configuration. Reason for specifying
     * factory method (instead of plain constructor) is to allow proper sub-classing of factories.
     * 
     * Note that custom subclasses generally **must override** implementation of this method, as it usually requires
     * instantiating a new instance of factory type. Check out docs for [BeanSerializerFactory] for more details.
     */
    protected abstract fun withConfig(config: SerializerFactoryConfig?): SerializerFactory

    /**
     * Convenience method for creating a new factory instance with an additional serializer provider.
     */
    final override fun withAdditionalSerializers(additional: Serializers): SerializerFactory {
        return withConfig(myFactoryConfig.withAdditionalSerializers(additional))
    }

    /**
     * Convenience method for creating a new factory instance with an additional key serializer provider.
     */
    final override fun withAdditionalKeySerializers(additional: Serializers): SerializerFactory {
        return withConfig(myFactoryConfig.withAdditionalKeySerializers(additional))
    }

    /**
     * Convenience method for creating a new factory instance with additional bean serializer modifier.
     */
    final override fun withSerializerModifier(modifier: ValueSerializerModifier): SerializerFactory {
        return withConfig(myFactoryConfig.withSerializerModifier(modifier))
    }

    final override fun withNullKeySerializers(serializer: ValueSerializer<*>): SerializerFactory {
        return withConfig(myFactoryConfig.withNullKeySerializer(serializer))
    }

    final override fun withNullValueSerializers(serializer: ValueSerializer<*>): SerializerFactory {
        return withConfig(myFactoryConfig.withNullValueSerializer(serializer))
    }

    /*
     *******************************************************************************************************************
     * SerializerFactory implementation
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    override fun createKeySerializer(context: SerializerProvider, type: KotlinType): ValueSerializer<Any>? {
        val beanDescription = context.introspectBeanDescription(type)
        val config = context.config
        var serializer: ValueSerializer<*>? = null

        if (myFactoryConfig.hasKeySerializers()) {
            for (serializers in myFactoryConfig.keySerializer()) {
                serializer = serializers.findSerializer(config, type, beanDescription, null)

                if (serializer != null) {
                    break
                }
            }
        }

        if (serializer == null) {
            serializer = findKeySerializer(context, beanDescription.classInfo) ?: JDKKeySerializers.getStdKeySerializer(
                    type.rawClass, false) ?: (beanDescription.findCirJsonKeyAccessor()
                    ?: beanDescription.findCirJsonValueAccessor())?.let {
                val delegate = createKeySerializer(context, it.type)

                if (config.canOverrideAccessModifiers()) {
                    it.member!!.checkAndFixAccess(config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
                }

                CirJsonValueSerializer.construct(config, type, it.type, false, null, delegate, it)
            } ?: JDKKeySerializers.getFallbackKeySerializer(config, type.rawClass, beanDescription.classInfo)
        }

        if (myFactoryConfig.hasSerializerModifiers()) {
            for (modifier in myFactoryConfig.serializerModifiers()) {
                serializer = modifier.modifyKeySerializer(config, type, beanDescription, serializer!!)
            }
        }

        return serializer as ValueSerializer<Any>
    }

    override val defaultNullKeySerializer: ValueSerializer<Any>
        get() = myFactoryConfig.nullKeySerializer

    override val defaultNullValueSerializer: ValueSerializer<Any>?
        get() = myFactoryConfig.nullValueSerializer

    /*
     *******************************************************************************************************************
     * Additional API for other core classes
     *******************************************************************************************************************
     */

    protected open fun customSerializers(): Iterable<Serializers> {
        return myFactoryConfig.serializers()
    }

    /**
     * Method called to create a type information serializer for values of given container property if one is needed. If
     * not needed (no polymorphic handling configured), should return `null`.
     *
     * @param containerType Declared type of the container to use as the base type for type information serializer
     *
     * @return Type serializer to use for property value contents, if one is needed; `null` if not.
     */
    open fun findPropertyContentTypeSerializer(context: SerializerProvider, containerType: KotlinType,
            member: AnnotatedMember): TypeSerializer? {
        return context.config.typeResolverProvider.findPropertyContentTypeSerializer(context, member, containerType)
    }

    /*
     *******************************************************************************************************************
     * Secondary serializer accessor methods
     *******************************************************************************************************************
     */

    /**
     * Method that will use fast lookup (and identity comparison) methods to see if we know serializer to use for given
     * type.
     */
    protected fun findSerializerByLookup(type: KotlinType): ValueSerializer<*>? {
        val raw = type.rawClass
        return JDKMiscellaneousSerializers.find(raw) ?: ourConcrete[raw.qualifiedName]
    }

    /**
     * Method called to see if one of primary per-class annotations (or related, like implementing of
     * [CirJacksonSerializable]) determines the serializer to use.
     * 
     * Currently, handles things like:
     * 
     * * If type implements [CirJacksonSerializable], use that
     * 
     * * If type has [org.cirjson.cirjackson.annotations.CirJsonValue] annotation (or equivalent), build serializer
     * based on that property
     */
    protected fun findSerializerByAnnotations(context: SerializerProvider, type: KotlinType,
            beanDescription: BeanDescription): ValueSerializer<*>? {
        val raw = type.rawClass

        if (CirJacksonSerializable::class.isAssignableFrom(raw)) {
            return CirJacksonSerializableSerializer.INSTANCE
        }

        val member = beanDescription.findCirJsonValueAccessor() ?: return null

        if (context.canOverrideAccessModifiers()) {
            member.member!!.checkAndFixAccess(context.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
        }

        val serializer = findSerializerFromAnnotation(context, member)
        val valueType = member.type
        val valueTypeSerializer = context.findTypeSerializer(valueType)
        return CirJsonValueSerializer.construct(context.config, type, valueType, false, valueTypeSerializer, serializer,
                member)
    }

    /**
     * Method for checking if we can determine serializer to use based on set of known primary types, checking for set
     * of known base types (exact matches having been compared against with `findSerializerByLookup`). This does not
     * include "secondary" interfaces, but mostly concrete or abstract base classes.
     */
    protected fun findSerializerByPrimaryType(context: SerializerProvider, type: KotlinType,
            beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?,
            staticTyping: Boolean): ValueSerializer<*>? {
        if (type.isTypeOrSubTypeOf(Calendar::class)) {
            return JavaUtilCalendarSerializer.INSTANCE
        }

        if (type.isTypeOrSubTypeOf(Date::class)) {
            return type.takeUnless { it.hasRawClass(Date::class) }
                    ?.let { OptionalHandlerFactory.INSTANCE.findSerializer(context.config, type) }
                    ?: JavaUtilDateSerializer.INSTANCE
        }

        if (type.isTypeOrSubTypeOf(Stream::class)) {
            return Jdk8StreamSerializer(type, context.typeFactory.findFirstTypeParameters(type, Stream::class))
        }

        if (type.isTypeOrSubTypeOf(Number::class)) {
            val format = calculateEffectiveFormat(beanDescription, Number::class, formatOverrides)

            return when (format.shape) {
                CirJsonFormat.Shape.STRING -> ToStringSerializer.INSTANCE
                CirJsonFormat.Shape.OBJECT -> null
                else -> NumberSerializer.INSTANCE
            }
        }

        if (type.isEnumType) {
            return buildEnumSerializer(context, type, beanDescription,
                    calculateEffectiveFormat(beanDescription, Enum::class, formatOverrides))
        }

        val raw = type.rawClass

        return when {
            Map.Entry::class.isAssignableFrom(raw) -> type.findSuperType(Map.Entry::class)!!.let {
                val keyType = it.containedTypeOrUnknown(0)
                val valueType = it.containedTypeOrUnknown(1)
                buildMapEntrySerializer(context, type, beanDescription,
                        calculateEffectiveFormat(beanDescription, Map.Entry::class, formatOverrides), staticTyping,
                        keyType, valueType)
            }

            ByteBuffer::class.isAssignableFrom(raw) -> ByteBufferSerializer()

            InetAddress::class.isAssignableFrom(raw) -> InetAddressSerializer()

            InetSocketAddress::class.isAssignableFrom(raw) -> InetSocketAddressSerializer()

            TimeZone::class.isAssignableFrom(raw) -> TimeZoneSerializer()

            Charset::class.isAssignableFrom(raw) -> ToStringSerializer.INSTANCE

            LongStream::class.isAssignableFrom(raw) -> LongStreamSerializer.INSTANCE

            IntStream::class.isAssignableFrom(raw) -> IntStreamSerializer.INSTANCE

            DoubleStream::class.isAssignableFrom(raw) -> DoubleStreamSerializer.INSTANCE

            Path::class.isAssignableFrom(raw) -> JDKStringLikeSerializer.find(Path::class)

            ClassLoader::class.isAssignableFrom(raw) -> ToEmptyObjectSerializer.construct(type)

            else -> OptionalHandlerFactory.INSTANCE.findSerializer(context.config, type)
        }
    }

    /**
     * Reflection-based serialized find method, which checks if given class implements one of recognized "add-on"
     * interfaces. Add-on here means a role that is usually or can be a secondary trait: for example, bean classes may
     * implement [Iterable], but their main function is usually something else.
     */
    protected fun findSerializerByAddonType(context: SerializerProvider, type: KotlinType,
            beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?,
            staticTyping: Boolean): ValueSerializer<*>? {
        val typeFactory = context.typeFactory

        return if (type.isTypeOrSubTypeOf(Iterator::class)) {
            buildIteratorSerializer(context, type, beanDescription, formatOverrides, staticTyping,
                    typeFactory.findFirstTypeParameters(type, Iterator::class))
        } else if (type.isTypeOrSubTypeOf(Iterable::class)) {
            buildIterableSerializer(context, type, beanDescription, formatOverrides, staticTyping,
                    typeFactory.findFirstTypeParameters(type, Iterable::class))
        } else if (type.isTypeOrSubTypeOf(CharSequence::class)) {
            ToStringSerializer.INSTANCE
        } else {
            null
        }
    }

    /**
     * Helper method called to check if a class or method has an annotation
     * [org.cirjson.cirjackson.databind.annotation.CirJsonSerialize.using] that tells the class to use for
     * serialization. Returns `null` if no such annotation found.
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun findSerializerFromAnnotation(context: SerializerProvider,
            annotated: Annotated): ValueSerializer<Any>? {
        val serializerDefinition =
                context.annotationIntrospector!!.findSerializer(context.config, annotated) ?: return null
        return findConvertingSerializer(context, annotated,
                context.serializerInstance(annotated, serializerDefinition)) as ValueSerializer<Any>?
    }

    /**
     * Helper method that will check whether given annotated entity (usually class, but may also be a property accessor)
     * indicates that a [Converter] is to be used; and if so, to construct and return suitable serializer for it. If
     * not, will simply return given serializer as is.
     */
    protected open fun findConvertingSerializer(context: SerializerProvider, annotated: Annotated,
            serializer: ValueSerializer<*>?): ValueSerializer<*>? {
        val converter = findConverter(context, annotated) ?: return serializer
        val delegateType = converter.getOutputType(context.typeFactory)
        return StandardDelegatingSerializer(converter, delegateType, serializer, null)
    }

    protected open fun findConverter(context: SerializerProvider, annotated: Annotated): Converter<Any, Any>? {
        return context.annotationIntrospector!!.findSerializationConverter(context.config, annotated)
                ?.let { context.converterInstance(annotated, it) }
    }

    /*
     *******************************************************************************************************************
     * Factory methods, container types
     *******************************************************************************************************************
     */

    protected open fun buildContainerSerializer(context: SerializerProvider, type: KotlinType,
            beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?,
            staticTyping: Boolean): ValueSerializer<*>? {
        var realStaticTyping = staticTyping

        if (!realStaticTyping && type.isUsedAsStaticType) {
            if (!type.isContainerType || !type.contentType!!.isJavaLangObject) {
                realStaticTyping = true
            }
        }

        val elementType = type.contentType!!
        val elementTypeSerializer = context.findTypeSerializer(elementType)

        if (elementTypeSerializer != null) {
            realStaticTyping = false
        }

        val elementValueSerializer = findContentSerializer(context, beanDescription.classInfo)
        val config = context.config

        if (type.isMapLikeType) {
            type as MapLikeType
            val keySerializer = findKeySerializer(context, beanDescription.classInfo)

            if (type is MapType) {
                return buildMapSerializer(context, type, beanDescription, formatOverrides, realStaticTyping,
                        keySerializer, elementTypeSerializer, elementValueSerializer)
            }

            var serializer: ValueSerializer<*>? = null

            for (serializers in customSerializers()) {
                serializer =
                        serializers.findMapLikeSerializer(config, type, beanDescription, formatOverrides, keySerializer,
                                elementTypeSerializer, elementValueSerializer)

                if (serializer != null) {
                    break
                }
            }

            if (serializer == null) {
                serializer = findSerializerByAnnotations(context, type, beanDescription)
            }

            serializer ?: return null

            if (myFactoryConfig.hasSerializerModifiers()) {
                for (modifier in myFactoryConfig.serializerModifiers()) {
                    serializer = modifier.modifyMapLikeSerializer(config, type, beanDescription, serializer!!)
                }
            }

            return serializer
        }

        if (type.isCollectionLikeType) {
            type as CollectionLikeType

            if (type is CollectionType) {
                return buildCollectionSerializer(context, type, beanDescription, formatOverrides, realStaticTyping,
                        elementTypeSerializer, elementValueSerializer)
            }

            var serializer: ValueSerializer<*>? = null

            for (serializers in customSerializers()) {
                serializer = serializers.findCollectionLikeSerializer(config, type, beanDescription, formatOverrides,
                        elementTypeSerializer, elementValueSerializer)

                if (serializer != null) {
                    break
                }
            }

            if (serializer == null) {
                serializer = findSerializerByAnnotations(context, type, beanDescription)
            }

            serializer ?: return null

            if (myFactoryConfig.hasSerializerModifiers()) {
                for (modifier in myFactoryConfig.serializerModifiers()) {
                    serializer = modifier.modifyCollectionLikeSerializer(config, type, beanDescription, serializer!!)
                }
            }

            return serializer
        }

        if (type.isArrayType) {
            return buildArraySerializer(context, type as ArrayType, beanDescription, formatOverrides, realStaticTyping,
                    elementTypeSerializer, elementValueSerializer)
        }

        return null
    }

    /**
     * Helper method that handles configuration details when constructing serializers for [List] types that support
     * efficient by-index access
     */
    protected open fun buildCollectionSerializer(context: SerializerProvider, type: CollectionType,
            beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?, staticTyping: Boolean,
            elementTypeSerializer: TypeSerializer?,
            elementValueSerializer: ValueSerializer<Any>?): ValueSerializer<*>? {
        val config = context.config
        var serializer: ValueSerializer<*>? = null

        for (serializers in customSerializers()) {
            serializer = serializers.findCollectionSerializer(config, type, beanDescription, formatOverrides,
                    elementTypeSerializer, elementValueSerializer)

            if (serializer != null) {
                break
            }
        }

        val format = calculateEffectiveFormat(beanDescription, Collection::class, formatOverrides)

        if (serializer == null) {
            serializer = findSerializerByAnnotations(context, type, beanDescription) ?: let {
                if (format.shape == CirJsonFormat.Shape.POJO) {
                    return null
                }

                val raw = type.rawClass

                if (EnumSet::class.isAssignableFrom(raw)) {
                    val enumType = type.contentType
                    buildEnumSetSerializer(enumType)
                } else {
                    val elementRaw = type.contentType.rawClass

                    when {
                        isIndexedList(raw) -> {
                            if (elementRaw == String::class) {
                                if (elementValueSerializer.isCirJacksonStandardImplementation) {
                                    IndexedStringListSerializer.INSTANCE
                                } else {
                                    null
                                }
                            } else {
                                buildIndexedListSerializer(type.contentType, staticTyping, elementTypeSerializer,
                                        elementValueSerializer)
                            }
                        }

                        elementRaw == String::class -> {
                            if (elementValueSerializer.isCirJacksonStandardImplementation) {
                                StringCollectionSerializer.INSTANCE
                            } else {
                                null
                            }
                        }

                        else -> {
                            null
                        }
                    } ?: buildCollectionSerializer(type.contentType, staticTyping, elementTypeSerializer,
                            elementValueSerializer)
                }
            }
        }

        if (myFactoryConfig.hasSerializerModifiers()) {
            for (modifier in myFactoryConfig.serializerModifiers()) {
                serializer = modifier.modifyCollectionLikeSerializer(config, type, beanDescription, serializer!!)
            }
        }

        return serializer
    }

    /*
     *******************************************************************************************************************
     * Factory methods, for Collections
     *******************************************************************************************************************
     */

    protected open fun isIndexedList(type: KClass<*>): Boolean {
        return RandomAccess::class.isAssignableFrom(type)
    }

    protected open fun buildIndexedListSerializer(elementType: KotlinType, staticTyping: Boolean,
            valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<Any>?): StandardContainerSerializer<*> {
        return IndexedListSerializer(elementType, staticTyping, valueTypeSerializer, valueSerializer)
    }

    protected open fun buildCollectionSerializer(elementType: KotlinType, staticTyping: Boolean,
            valueTypeSerializer: TypeSerializer?,
            valueSerializer: ValueSerializer<Any>?): StandardContainerSerializer<*> {
        return CollectionSerializer(elementType, staticTyping, valueTypeSerializer, valueSerializer)
    }

    protected open fun buildEnumSetSerializer(enumType: KotlinType): ValueSerializer<*> {
        return EnumSetSerializer(enumType)
    }

    /*
     *******************************************************************************************************************
     * Factory methods, for Maps
     *******************************************************************************************************************
     */

    /**
     * Helper method that handles configuration details when constructing serializers for [Map] types.
     */
    protected open fun buildMapSerializer(context: SerializerProvider, type: MapType, beanDescription: BeanDescription,
            formatOverrides: CirJsonFormat.Value?, staticTyping: Boolean, keySerializer: ValueSerializer<Any>?,
            elementTypeSerializer: TypeSerializer?,
            elementValueSerializer: ValueSerializer<Any>?): ValueSerializer<*>? {
        val format = calculateEffectiveFormat(beanDescription, Map::class, formatOverrides)

        if (format.shape == CirJsonFormat.Shape.POJO) {
            return null
        }

        var serializer: ValueSerializer<*>? = null

        val config = context.config

        for (serializers in customSerializers()) {
            serializer = serializers.findMapSerializer(config, type, beanDescription, formatOverrides, keySerializer,
                    elementTypeSerializer, elementValueSerializer)

            if (serializer != null) {
                break
            }
        }

        if (serializer == null) {
            serializer = findSerializerByAnnotations(context, type, beanDescription) ?: findFilterId(config,
                    beanDescription).let {
                val ignorals = config.getDefaultPropertyIgnorals(Map::class, beanDescription.classInfo)
                val ignored = ignorals?.findIgnoredForSerialization()
                val inclusions = config.getDefaultPropertyInclusions(Map::class, beanDescription.classInfo)
                val included = inclusions?.included
                val mapSerializer = MapSerializer.construct(type, staticTyping, elementTypeSerializer, keySerializer,
                        elementValueSerializer, it, ignored, included)
                checkMapContentInclusion(context, beanDescription, mapSerializer)
            }
        }

        if (myFactoryConfig.hasSerializerModifiers()) {
            for (modifier in myFactoryConfig.serializerModifiers()) {
                serializer = modifier.modifyMapSerializer(config, type, beanDescription, serializer!!)
            }
        }

        return serializer
    }

    /**
     * Helper method that does figures out content inclusion value to use, if any, and construct re-configured
     * [MapSerializer] appropriately.
     */
    protected open fun checkMapContentInclusion(context: SerializerProvider, beanDescription: BeanDescription,
            mapSerializer: MapSerializer): MapSerializer {
        val contentType = mapSerializer.contentType
        val includeValue = findInclusionWithContent(context, beanDescription, contentType, Map::class)
        val include = includeValue?.contentInclusion ?: return mapSerializer

        if (include == CirJsonInclude.Include.USE_DEFAULTS || include == CirJsonInclude.Include.ALWAYS) {
            return mapSerializer
        }

        var suppressNulls = true

        val valueToSuppress = when (include) {
            CirJsonInclude.Include.NON_DEFAULT -> BeanUtil.getDefaultValue(contentType)?.let {
                if (it::class.isArray) {
                    ArrayBuilders.getArrayComparator(it)
                } else {
                    it
                }
            }

            CirJsonInclude.Include.NON_ABSENT -> MapSerializer.MARKER_FOR_EMPTY.takeIf { contentType.isReferenceType }

            CirJsonInclude.Include.NON_EMPTY -> MapSerializer.MARKER_FOR_EMPTY

            CirJsonInclude.Include.CUSTOM -> context.includeFilterInstance(null, includeValue.contentFilter)?.also {
                suppressNulls = context.includeFilterSuppressNulls(it)
            }

            CirJsonInclude.Include.NON_NULL -> null
        }

        return mapSerializer.withContentInclusionInternal(valueToSuppress, suppressNulls)
    }

    protected open fun buildMapEntrySerializer(context: SerializerProvider, type: KotlinType,
            beanDescription: BeanDescription, effectiveFormat: CirJsonFormat.Value, staticTyping: Boolean,
            keyType: KotlinType, valueType: KotlinType): ValueSerializer<*>? {
        if (effectiveFormat.shape == CirJsonFormat.Shape.POJO) {
            return null
        }

        val serializer =
                MapEntrySerializer(valueType, keyType, valueType, staticTyping, context.findTypeSerializer(valueType),
                        null)

        val contentType = serializer.contentType
        val includeValue = findInclusionWithContent(context, beanDescription, contentType, Map.Entry::class)

        val include = includeValue?.contentInclusion ?: return serializer

        if (include == CirJsonInclude.Include.USE_DEFAULTS || include == CirJsonInclude.Include.ALWAYS) {
            return serializer
        }

        var suppressNulls = true

        val valueToSuppress = when (include) {
            CirJsonInclude.Include.NON_DEFAULT -> BeanUtil.getDefaultValue(contentType)?.let {
                if (it::class.isArray) {
                    ArrayBuilders.getArrayComparator(it)
                } else {
                    it
                }
            }

            CirJsonInclude.Include.NON_ABSENT -> MapSerializer.MARKER_FOR_EMPTY.takeIf { contentType.isReferenceType }

            CirJsonInclude.Include.NON_EMPTY -> MapSerializer.MARKER_FOR_EMPTY

            CirJsonInclude.Include.CUSTOM -> context.includeFilterInstance(null, includeValue.contentFilter)?.also {
                suppressNulls = context.includeFilterSuppressNulls(it)
            }

            CirJsonInclude.Include.NON_NULL -> null
        }

        return serializer.withContentInclusion(valueToSuppress, suppressNulls)
    }

    /**
     * Helper method used for finding inclusion definitions for structured container types like `Map`s and referential
     * types (like `AtomicReference`).
     *
     * @param contentType Declared full content type of container
     * 
     * @param configType Raw base type under which `configOverride`, if any, needs to be defined
     */
    protected open fun findInclusionWithContent(context: SerializerProvider, beanDescription: BeanDescription,
            contentType: KotlinType, configType: KClass<*>): CirJsonInclude.Value? {
        val config = context.config

        val defaultIncludeValue = beanDescription.findPropertyInclusion(config.defaultPropertyInclusion)!!
        val includeValue = config.getDefaultPropertyInclusion(configType, defaultIncludeValue)!!

        val inclusion = config.getDefaultPropertyInclusion(contentType.rawClass, null) ?: return includeValue

        return when (inclusion.valueInclusion) {
            CirJsonInclude.Include.USE_DEFAULTS -> includeValue
            CirJsonInclude.Include.CUSTOM -> includeValue.withContentFilter(inclusion.contentFilter)
            else -> includeValue.withContentInclusion(inclusion.valueInclusion)
        }
    }

    /*
     *******************************************************************************************************************
     * Factory methods, for Arrays
     *******************************************************************************************************************
     */

    /**
     * Helper method that handles configuration details when constructing serializers for `Array<Any?>` (and subtypes,
     * except for String).
     */
    protected open fun buildArraySerializer(context: SerializerProvider, type: ArrayType,
            beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?, staticTyping: Boolean,
            elementTypeSerializer: TypeSerializer?,
            elementValueSerializer: ValueSerializer<Any>?): ValueSerializer<*>? {
        val config = context.config
        var serializer: ValueSerializer<*>? = null

        for (serializers in customSerializers()) {
            serializer = serializers.findArraySerializer(config, type, beanDescription, formatOverrides,
                    elementTypeSerializer, elementValueSerializer)

            if (serializer != null) {
                break
            }
        }

        if (serializer == null) {
            val raw = type.rawClass

            serializer = if (elementValueSerializer.isCirJacksonStandardImplementation) {
                if (Array<String>::class == raw) {
                    StringArraySerializer.INSTANCE
                } else {
                    JDKArraySerializers.findStandardImplementation(raw)
                }
            } else {
                null
            } ?: ObjectArraySerializer(type.contentType, staticTyping, elementTypeSerializer, elementValueSerializer)
        }

        if (myFactoryConfig.hasSerializerModifiers()) {
            for (modifier in myFactoryConfig.serializerModifiers()) {
                serializer = modifier.modifyArraySerializer(config, type, beanDescription, serializer!!)
            }
        }

        return serializer
    }

    /*
     *******************************************************************************************************************
     * Factory methods, for Reference types
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    protected open fun findReferenceSerializer(context: SerializerProvider, referenceType: ReferenceType,
            beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?,
            staticTyping: Boolean): ValueSerializer<*>? {
        val config = context.config
        val contentType = referenceType.contentType
        val contentTypeSerializer =
                contentType.typeHandler as TypeSerializer? ?: context.findTypeSerializer(contentType)
        val contentSerializer = contentType.valueHandler as ValueSerializer<Any>?

        for (serializers in customSerializers()) {
            val serializer =
                    serializers.findReferenceSerializer(config, referenceType, beanDescription, formatOverrides,
                            contentTypeSerializer, contentSerializer)

            if (serializer != null) {
                return serializer
            }
        }

        return when {
            referenceType.isTypeOrSubTypeOf(AtomicReference::class) -> buildReferenceSerializer(context,
                    AtomicReference::class, referenceType, beanDescription, staticTyping, contentTypeSerializer,
                    contentSerializer)

            referenceType.isTypeOrSubTypeOf(Optional::class) -> buildReferenceSerializer(context, Optional::class,
                    referenceType, beanDescription, staticTyping, contentTypeSerializer, contentSerializer)

            referenceType.isTypeOrSubTypeOf(OptionalInt::class) -> OptionalIntSerializer()
            referenceType.isTypeOrSubTypeOf(OptionalLong::class) -> OptionalLongSerializer()
            referenceType.isTypeOrSubTypeOf(OptionalDouble::class) -> OptionalDoubleSerializer()
            else -> null
        }
    }

    protected open fun buildReferenceSerializer(context: SerializerProvider, baseType: KClass<*>,
            referenceType: ReferenceType, beanDescription: BeanDescription, staticTyping: Boolean,
            contentTypeSerializer: TypeSerializer?, contentSerializer: ValueSerializer<Any>?): ValueSerializer<*>? {
        val contentType = referenceType.referencedType
        val includeValue = findInclusionWithContent(context, beanDescription, contentType, baseType)
        val include = includeValue?.contentInclusion ?: CirJsonInclude.Include.USE_DEFAULTS

        if (include == CirJsonInclude.Include.USE_DEFAULTS || include == CirJsonInclude.Include.ALWAYS) {
            val serializer = if (baseType == Optional::class) {
                Jdk8OptionalSerializer(referenceType, contentTypeSerializer, contentSerializer)
            } else {
                AtomicReferenceSerializer(referenceType, contentTypeSerializer, contentSerializer)
            }

            return serializer.withContentInclusionInternal(null, false)
        }

        var suppressNulls = true

        val valueToSuppress = when (include) {
            CirJsonInclude.Include.NON_DEFAULT -> BeanUtil.getDefaultValue(contentType)?.let {
                if (it::class.isArray) {
                    ArrayBuilders.getArrayComparator(it)
                } else {
                    it
                }
            }

            CirJsonInclude.Include.NON_ABSENT -> MapSerializer.MARKER_FOR_EMPTY.takeIf { contentType.isReferenceType }

            CirJsonInclude.Include.NON_EMPTY -> MapSerializer.MARKER_FOR_EMPTY

            CirJsonInclude.Include.CUSTOM -> context.includeFilterInstance(null, includeValue?.contentFilter)?.also {
                suppressNulls = context.includeFilterSuppressNulls(it)
            }

            CirJsonInclude.Include.NON_NULL -> null
        }

        val serializer = if (baseType == Optional::class) {
            Jdk8OptionalSerializer(referenceType, contentTypeSerializer, contentSerializer)
        } else {
            AtomicReferenceSerializer(referenceType, contentTypeSerializer, contentSerializer)
        }

        return serializer.withContentInclusionInternal(valueToSuppress, suppressNulls)
    }

    /*
     *******************************************************************************************************************
     * Factory methods, for non-container types
     *******************************************************************************************************************
     */

    protected open fun buildIteratorSerializer(context: SerializerProvider, type: KotlinType,
            beanDescription: BeanDescription, formatOverrides: CirJsonFormat.Value?, staticTyping: Boolean,
            valueType: KotlinType): ValueSerializer<*>? {
        return IteratorSerializer(valueType, staticTyping, context.findTypeSerializer(valueType))
    }

    protected open fun buildIterableSerializer(context: SerializerProvider, type: KotlinType,
            beanDescription: BeanDescription, effectiveFormat: CirJsonFormat.Value?, staticTyping: Boolean,
            valueType: KotlinType): ValueSerializer<*>? {
        return IterableSerializer(valueType, staticTyping, context.findTypeSerializer(valueType))
    }

    @Suppress("UNCHECKED_CAST")
    protected open fun buildEnumSerializer(context: SerializerProvider, type: KotlinType,
            beanDescription: BeanDescription, effectiveFormat: CirJsonFormat.Value): ValueSerializer<*>? {
        val shape = effectiveFormat.shape

        if (shape == CirJsonFormat.Shape.POJO || shape == CirJsonFormat.Shape.OBJECT) {
            (beanDescription as BasicBeanDescription).removeProperty("declaringClass")

            if (type.isEnumType) {
                removeEnumSelfReferences(beanDescription)
            }

            return null
        }

        val enumClass = type.rawClass as KClass<Enum<*>>
        val config = context.config
        var serializer: ValueSerializer<*> =
                EnumSerializer.construct(enumClass, config, beanDescription, effectiveFormat)

        if (myFactoryConfig.hasSerializerModifiers()) {
            for (modifier in myFactoryConfig.serializerModifiers()) {
                serializer = modifier.modifyEnumSerializer(config, type, beanDescription, serializer)
            }
        }

        return serializer
    }

    /**
     * Helper method used for serialization [Enum] as [CirJsonFormat.Shape.OBJECT]. Removes any self-referencing
     * properties from its bean description before it is transformed into a JSON Object as configured by
     * [CirJsonFormat.Shape.OBJECT].
     * 
     * Internally, this method iterates through [BeanDescription.findProperties] and removes self references.
     *
     * @param beanDescription the bean description to remove Enum properties from.
     */
    private fun removeEnumSelfReferences(beanDescription: BeanDescription) {
        val type = beanDescription.beanClass.findEnumType()
        val iterator = beanDescription.findProperties().iterator()

        while (iterator.hasNext()) {
            val property = iterator.next()
            val propertyType = property.primaryType

            if (propertyType.isEnumType && propertyType.isTypeOrSubTypeOf(type) && property.accessor!!.isStatic) {
                iterator.remove()
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Other helper methods
     *******************************************************************************************************************
     */

    /**
     * Helper method that will combine all available pieces of format configuration and calculate effective format
     * settings to use.
     */
    protected open fun calculateEffectiveFormat(beanDescription: BeanDescription, baseType: KClass<*>,
            formatOverrides: CirJsonFormat.Value?): CirJsonFormat.Value {
        val fromType = beanDescription.findExpectedFormat(baseType)!!
        return formatOverrides?.let { CirJsonFormat.Value.merge(fromType, it) } ?: fromType
    }

    /**
     * Helper method called to try to find whether there is an annotation in the class that indicates key serializer to
     * use. If so, will try to instantiate key serializer and return it; otherwise returns `null`.
     */
    protected open fun findKeySerializer(context: SerializerProvider, annotated: Annotated): ValueSerializer<Any>? {
        val serializerDefinition = context.annotationIntrospector!!.findKeySerializer(context.config, annotated)
        return context.serializerInstance(annotated, serializerDefinition)
    }

    /**
     * Helper method called to try to find whether there is an annotation in the class that indicates content ("value")
     * serializer to use. If so, will try to instantiate value serializer and return it; otherwise returns `null`.
     */
    protected open fun findContentSerializer(context: SerializerProvider, annotated: Annotated): ValueSerializer<Any>? {
        val serializerDefinition = context.annotationIntrospector!!.findContentSerializer(context.config, annotated)
        return context.serializerInstance(annotated, serializerDefinition)
    }

    /**
     * Method called to find filter that is configured to be used with bean serializer being built, if any.
     */
    protected open fun findFilterId(config: SerializationConfig, beanDescription: BeanDescription): Any? {
        return config.annotationIntrospector!!.findFilterId(config, beanDescription.classInfo)
    }

    /**
     * Helper method to check whether global settings and/or class annotations for the bean class indicate that static
     * typing (declared types)  should be used for properties. (instead of dynamic runtime types).
     */
    protected open fun usesStaticTyping(config: SerializationConfig, beanDescription: BeanDescription): Boolean {
        return when (config.annotationIntrospector!!.findSerializationTyping(config, beanDescription.classInfo)) {
            CirJsonSerialize.Typing.DYNAMIC -> false
            CirJsonSerialize.Typing.STATIC -> true
            else -> config.isEnabled(MapperFeature.USE_STATIC_TYPING)
        }
    }

    companion object {

        /*
         ***************************************************************************************************************
         * Configuration, lookup tables/maps
         ***************************************************************************************************************
         */

        val ourConcrete = HashMap<String, ValueSerializer<*>>().also {
            it[String::class.qualifiedName!!] = StringSerializer.INSTANCE
            val toStringSerializer = ToStringSerializer.INSTANCE
            it[StringBuffer::class.qualifiedName!!] = toStringSerializer
            it[StringBuilder::class.qualifiedName!!] = toStringSerializer
            it[Char::class.qualifiedName!!] = toStringSerializer

            NumberSerializers.addAll(it)

            it[Boolean::class.qualifiedName!!] = BooleanSerializer(true)
            it[java.lang.Boolean.TYPE.name] = BooleanSerializer(false)
            it[java.lang.Boolean::class.qualifiedName!!] = BooleanSerializer(false)

            it[BigInteger::class.qualifiedName!!] = NumberSerializer(BigInteger::class)
            it[BigDecimal::class.qualifiedName!!] = NumberSerializer(BigDecimal::class)

            it[Calendar::class.qualifiedName!!] = JavaUtilCalendarSerializer()
            it[Date::class.qualifiedName!!] = JavaUtilDateSerializer()
        }

    }

}