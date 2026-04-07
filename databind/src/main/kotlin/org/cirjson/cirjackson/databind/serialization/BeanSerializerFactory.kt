package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdGenerators
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.configuration.SerializerFactoryConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.serialization.implementation.FilteredBeanPropertyWriters
import org.cirjson.cirjackson.databind.serialization.implementation.ObjectIdWriter
import org.cirjson.cirjackson.databind.serialization.implementation.PropertyBasedObjectIdGenerator
import org.cirjson.cirjackson.databind.serialization.implementation.UnsupportedTypeSerializer
import org.cirjson.cirjackson.databind.serialization.jdk.MapSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardDelegatingSerializer
import org.cirjson.cirjackson.databind.serialization.standard.ToEmptyObjectSerializer
import org.cirjson.cirjackson.databind.type.ReferenceType
import org.cirjson.cirjackson.databind.util.*
import kotlin.reflect.KClass

/**
 * Factory class that can provide serializers for any regular beans (as defined by "having at least one get method
 * recognizable as bean accessor" -- where `Any::class` does not count); as well as for "standard" types. Latter is
 * achieved by delegating calls to [BasicSerializerFactory] to find serializers both for "standard" types (and in some
 * cases, subclasses as is the case for collection classes like [Lists][List] and [Maps][Map]) and bean (value) classes.
 * 
 * Note about delegating calls to [BasicSerializerFactory]: although it would be nicer to use linear delegation for
 * construction (to essentially dispatch all calls first to the underlying [BasicSerializerFactory]; or alternatively
 * after failing to provide bean-based serializer), there is a problem: priority levels for detecting standard types are
 * mixed. That is, we want to check if a type is a bean after some of "standard" types, but before the rest. As a
 * result, "mixed" delegation used, and calls are NOT done using regular [SerializerFactory] interface but rather via
 * direct calls to [BasicSerializerFactory].
 * 
 * Finally, since all caching is handled by the serializer provider (not factory) and there is no configurability, this
 * factory is stateless. This means that a global singleton instance can be used.
 * 
 * @constructor Constructor for creating instances with specified configuration.
 */
open class BeanSerializerFactory protected constructor(config: SerializerFactoryConfig?) :
        BasicSerializerFactory(config) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Method used by module registration functionality, to attach additional serializer providers into this serializer
     * factory. This is typically handled by constructing a new instance with additional serializers, to ensure
     * thread-safe access.
     */
    override fun withConfig(config: SerializerFactoryConfig?): SerializerFactory {
        if (myFactoryConfig === config) {
            return this
        }

        verifyMustOverride(BeanSerializerFactory::class, this, "withConfig")
        return BeanSerializerFactory(config)
    }

    /*
     *******************************************************************************************************************
     * SerializerFactory implementation
     *******************************************************************************************************************
     */

    /**
     * Main serializer constructor method. We will have to be careful with respect to ordering of various method calls:
     * essentially we want to reliably figure out which classes are standard types, and which are beans. The problem is
     * that some bean Classes may implement standard interfaces (say, [Iterable].
     * 
     * Note: subclasses may choose to complete replace implementation, if they want to alter priority of serializer
     * lookups.
     */
    @Suppress("UNCHECKED_CAST")
    override fun createSerializer(context: SerializerProvider, baseType: KotlinType, beanDescription: BeanDescription,
            formatOverride: CirJsonFormat.Value?): ValueSerializer<Any> {
        var realBeanDescription = beanDescription
        var serializer: ValueSerializer<*>? = findSerializerFromAnnotation(context, realBeanDescription.classInfo)

        if (serializer != null) {
            return serializer as ValueSerializer<Any>
        }

        val config = context.config
        val introspector = config.annotationIntrospector

        val type = try {
            introspector?.refineSerializationType(config, beanDescription.classInfo, baseType) ?: baseType
        } catch (e: CirJacksonException) {
            return context.reportBadTypeDefinition(realBeanDescription, e.message)
        }

        val staticTyping = if (type === baseType) {
            false
        } else {
            if (!type.hasRawClass(baseType.rawClass)) {
                realBeanDescription = context.introspectBeanDescription(type)
            }

            true
        }

        val converter = realBeanDescription.findSerializationConverter() ?: return createSerializer(context,
                realBeanDescription, type, formatOverride, staticTyping) as ValueSerializer<Any>
        val delegateType = converter.getOutputType(context.typeFactory)

        if (!delegateType.hasRawClass(type.rawClass)) {
            realBeanDescription = context.introspectBeanDescription(delegateType)
            serializer = findSerializerFromAnnotation(context, realBeanDescription.classInfo)
        }

        if (serializer == null && !delegateType.isJavaLangObject) {
            serializer = createSerializer(context, realBeanDescription, delegateType, formatOverride, true)
        }

        return StandardDelegatingSerializer(converter, delegateType, serializer, null)
    }

    protected open fun createSerializer(context: SerializerProvider, beanDescription: BeanDescription, type: KotlinType,
            formatOverride: CirJsonFormat.Value?, staticTyping: Boolean): ValueSerializer<*> {
        var realStaticTyping = staticTyping
        val config = context.config

        var serializer: ValueSerializer<*> = if (type.isContainerType) {
            if (!realStaticTyping) {
                realStaticTyping = usesStaticTyping(config, beanDescription)
            }

            buildContainerSerializer(context, type, beanDescription, formatOverride,
                    realStaticTyping)?.also { return it }
        } else {
            if (type.isReferenceType) {
                findReferenceSerializer(context, type as ReferenceType, beanDescription, formatOverride,
                        realStaticTyping)
            } else {
                let {
                    for (serializers in customSerializers()) {
                        serializers.findSerializer(config, type, beanDescription, formatOverride)
                                ?.also { return@let it }
                    }

                    null
                }
            } ?: findSerializerByAnnotations(context, type, beanDescription)
        } ?: findSerializerByLookup(type) ?: findSerializerByPrimaryType(context, type, beanDescription, formatOverride,
                realStaticTyping) ?: constructBeanOrAddOnSerializer(context, type, beanDescription, formatOverride,
                realStaticTyping) ?: context.getUnknownTypeSerializer(beanDescription.beanClass)

        if (myFactoryConfig.hasSerializerModifiers()) {
            for (modifier in myFactoryConfig.serializerModifiers()) {
                serializer = modifier.modifySerializer(config, beanDescription, serializer)
            }
        }

        return serializer
    }

    /*
     *******************************************************************************************************************
     * Overridable non-public factory methods
     *******************************************************************************************************************
     */

    /**
     * Method called to construct serializer based on checking which condition is matched:
     *
     * 1. Nominal type is `Any`: if so, return special "no type known" serializer
     *
     * 2. If a known "not-POJO" type (like JDK `Proxy`), return `null`
     *
     * 3. If at least one logical property found, build actual [BeanSerializer]
     *
     * 4. If add-on type (like [Iterable]) found, create appropriate serializer
     *
     * 5. If one of CirJackson's "well-known" annotations found, create bogus "empty Object" Serializer
     *
     * or, if none matched, return `null`.
     */
    @Suppress("UNCHECKED_CAST")
    protected open fun constructBeanOrAddOnSerializer(context: SerializerProvider, type: KotlinType,
            beanDescription: BeanDescription, formatOverride: CirJsonFormat.Value?,
            staticTyping: Boolean): ValueSerializer<Any>? {
        if (beanDescription.beanClass == Any::class) {
            return context.getUnknownTypeSerializer(Any::class)
        }

        if (!isPotentialBeanType(type.rawClass) && !type.isEnumType) {
            return null
        }

        var serializer = findUnsupportedTypeSerializer(context, type, beanDescription)

        if (serializer != null) {
            return serializer as ValueSerializer<Any>
        }

        if (isUnserializableCirJacksonType(context, type)) {
            return ToEmptyObjectSerializer.construct(type)
        }

        val config = context.config
        var builder = constructBeanSerializerBuilder(config, beanDescription)

        var properties = findBeanProperties(context, beanDescription, builder)?.let {
            removeOverlappingTypeIds(context, beanDescription, builder, it)
        } ?: ArrayList()

        context.annotationIntrospector!!.findAndAddVirtualProperties(config, beanDescription.classInfo, properties)

        if (myFactoryConfig.hasSerializerModifiers()) {
            for (modifier in myFactoryConfig.serializerModifiers()) {
                properties = modifier.changeProperties(config, beanDescription, properties)
            }
        }

        properties = filterUnwantedJDKProperties(config, beanDescription, properties)
        properties = filterBeanProperties(config, beanDescription, properties)

        if (myFactoryConfig.hasSerializerModifiers()) {
            for (modifier in myFactoryConfig.serializerModifiers()) {
                properties = modifier.orderProperties(config, beanDescription, properties)
            }
        }

        builder.objectIdWriter = constructObjectIdHandler(context, beanDescription, properties)
        builder.properties = properties
        builder.filterId = findFilterId(config, beanDescription)

        val anyGetter = beanDescription.findAnyGetter()

        if (anyGetter != null) {
            val anyType = anyGetter.type
            val valueType = anyType.contentType!!
            val typeSerializer = context.findTypeSerializer(valueType)
            val anySerializer = findSerializerFromAnnotation(context, anyGetter) ?: MapSerializer.construct(anyType,
                    config.isEnabled(MapperFeature.USE_STATIC_TYPING), typeSerializer, null, null, null, null, null)
            val name = PropertyName.construct(anyGetter.name)
            val anyProperty =
                    BeanProperty.Standard(name, valueType, null, anyGetter, PropertyMetadata.STANDARD_OPTIONAL)
            builder.anyGetter = AnyGetterWriter(anyProperty, anyGetter, anySerializer)
        }

        processViews(config, builder)

        if (myFactoryConfig.hasSerializerModifiers()) {
            for (modifier in myFactoryConfig.serializerModifiers()) {
                builder = modifier.updateBuilder(config, beanDescription, builder)
            }
        }

        try {
            serializer = builder.build()
        } catch (e: RuntimeException) {
            return context.reportBadTypeDefinition(beanDescription,
                    "Failed to construct BeanSerializer for ${beanDescription.type}: (${e::class.qualifiedName}) ${e.message}")
        }

        if (serializer != null) {
            return serializer as ValueSerializer<Any>
        }

        if (type.isRecordType && !NativeImageUtil.needsReflectionConfiguration(type.rawClass)) {
            return builder.createDummy()
        }

        serializer = findSerializerByAddonType(context, type, beanDescription, formatOverride, staticTyping)

        if (serializer != null) {
            return serializer as ValueSerializer<Any>
        }

        return if (beanDescription.hasKnownClassAnnotations()) {
            builder.createDummy()
        } else {
            null
        }
    }

    protected open fun constructObjectIdHandler(context: SerializerProvider, beanDescription: BeanDescription,
            properties: MutableList<BeanPropertyWriter>): ObjectIdWriter? {
        val objectIdInfo = beanDescription.objectIdInfo ?: return null
        val implementationClass = objectIdInfo.generatorType

        if (implementationClass == ObjectIdGenerators.PropertyGenerator::class) {
            val propertyName = objectIdInfo.propertyName.simpleName

            val idProperty: BeanPropertyWriter

            val length = properties.size
            var i = 0

            while (true) {
                if (i >= length) {
                    throw IllegalArgumentException(
                            "Invalid Object Id definition for ${beanDescription.type.typeDescription}: cannot find property with name ${propertyName.name()}")
                }

                val property = properties[i]

                if (propertyName == property.name) {
                    idProperty = property

                    if (i > 0) {
                        properties.removeAt(i)
                        properties.add(0, idProperty)
                    }

                    break
                }

                i++
            }

            val idType = idProperty.type
            val generator = PropertyBasedObjectIdGenerator(objectIdInfo, idProperty)
            return ObjectIdWriter.construct(idType, null, generator, objectIdInfo.alwaysAsId)
        }

        val type = context.constructType(implementationClass)!!
        val idType = context.typeFactory.findTypeParameters(type, ObjectIdGenerator::class).first()!!
        val generator = context.objectIdGeneratorInstance(beanDescription.classInfo, objectIdInfo)
        return ObjectIdWriter.construct(idType, objectIdInfo.propertyName, generator, objectIdInfo.alwaysAsId)
    }

    /**
     * Method called to construct a filtered writer, for given view definitions. Default implementation constructs
     * filter that checks active view type to views property is to be included in.
     */
    protected open fun constructFilteredBeanWriter(writer: BeanPropertyWriter,
            inViews: Array<KClass<*>>): BeanPropertyWriter {
        return FilteredBeanPropertyWriters.constructViewBased(writer, inViews)
    }

    protected open fun constructPropertyBuilder(config: SerializationConfig,
            beanDescription: BeanDescription): PropertyBuilder {
        return PropertyBuilder(config, beanDescription)
    }

    protected open fun constructBeanSerializerBuilder(config: SerializationConfig,
            beanDescription: BeanDescription): BeanSerializerBuilder {
        return BeanSerializerBuilder(config, beanDescription)
    }

    /*
     *******************************************************************************************************************
     * Overridable non-public introspection methods
     *******************************************************************************************************************
     */

    /**
     * Helper method used to skip processing for types that we know cannot be (i.e. are never consider to be) beans:
     * things like primitives, Arrays, Enums, and proxy types.
     *
     * Note that usually we shouldn't really be getting these sort of types anyway; but better safe than sorry.
     */
    protected open fun isPotentialBeanType(type: KClass<*>): Boolean {
        return type.canBeABeanType() == null && !type.isProxyType
    }

    /**
     * Method used to collect all actual serializable properties. Can be overridden to implement custom detection
     * schemes.
     */
    protected open fun findBeanProperties(context: SerializerProvider, beanDescription: BeanDescription,
            builder: BeanSerializerBuilder): MutableList<BeanPropertyWriter>? {
        val properties = beanDescription.findProperties()
        val config = context.config

        removeIgnorableTypes(context, beanDescription, properties)

        if (config.isEnabled(MapperFeature.REQUIRE_SETTERS_FOR_GETTERS)) {
            removeSetterlessGetters(config, beanDescription, properties)
        }

        if (properties.isEmpty()) {
            return null
        }

        val staticTyping = usesStaticTyping(config, beanDescription)
        val propertyBuilder = constructPropertyBuilder(config, beanDescription)

        val result = ArrayList<BeanPropertyWriter>(properties.size)

        for (property in properties) {
            val accessor = property.accessor

            if (property.isTypeId) {
                if (accessor != null) {
                    builder.typeId = accessor
                }

                continue
            }

            val referenceType = property.findReferenceType()

            if (referenceType?.isBackReference ?: false) {
                continue
            }

            result.add(constructWriter(context, property, propertyBuilder, staticTyping, accessor!!))
        }

        return result
    }

    /*
     *******************************************************************************************************************
     * Overridable non-public methods for manipulating bean properties
     *******************************************************************************************************************
     */

    /**
     * Overridable method that can filter out properties. Default implementation checks annotations class may have.
     */
    protected open fun filterBeanProperties(config: SerializationConfig, beanDescription: BeanDescription,
            properties: MutableList<BeanPropertyWriter>): MutableList<BeanPropertyWriter> {
        val ignorals = config.getDefaultPropertyIgnorals(beanDescription.beanClass, beanDescription.classInfo)
        val ignored = ignorals?.findIgnoredForSerialization()
        val inclusions = config.getDefaultPropertyInclusions(beanDescription.beanClass, beanDescription.classInfo)
        val included = inclusions?.included

        if (included == null && ignored.isNullOrEmpty()) {
            return properties
        }

        val iterator = properties.iterator()

        while (iterator.hasNext()) {
            if (IgnorePropertiesUtil.shouldIgnore(iterator.next().name, ignored, included)) {
                iterator.remove()
            }
        }

        return properties
    }

    /**
     * Overridable method used to filter out specifically problematic JDK provided properties.
     */
    protected open fun filterUnwantedJDKProperties(config: SerializationConfig, beanDescription: BeanDescription,
            properties: MutableList<BeanPropertyWriter>): MutableList<BeanPropertyWriter> {
        if (!beanDescription.type.isTypeOrSubTypeOf(CharSequence::class)) {
            return properties
        }

        if (properties.size != 1) {
            return properties
        }

        val property = properties[0]
        val member = property.member

        if (member is AnnotatedMethod && "isEmpty" == member.name && member.declaringClass == CharSequence::class) {
            properties.removeAt(0)
        }

        return properties
    }

    /**
     * Method called to handle view information for constructed serializer, based on bean property writers.
     *
     * Note that this method is designed to be overridden by subclasses if they want to provide custom view handling. As
     * such, it is not considered an internal implementation detail, and is supported as part of API.
     */
    protected open fun processViews(config: SerializationConfig, builder: BeanSerializerBuilder) {
        val properties = builder.properties
        val includeByDefault = config.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION)
        var viewsFound = 0

        val filtered = Array(properties.size) { i ->
            val beanPropertyWriter = properties[i]
            val views = beanPropertyWriter.views

            if (views.isNullOrEmpty()) {
                if (includeByDefault) {
                    beanPropertyWriter
                } else {
                    null
                }
            } else {
                viewsFound++
                constructFilteredBeanWriter(beanPropertyWriter, views)
            }
        }

        if (includeByDefault && viewsFound == 0) {
            return
        }

        builder.filteredProperties = filtered
    }

    /**
     * Method that will apply by-type limitations; by default this is based on
     * [org.cirjson.cirjackson.annotations.CirJsonIgnoreType] annotation but can be supplied by module-provided
     * introspectors too. There are also "Config overrides" to consider.
     */
    protected open fun removeIgnorableTypes(context: SerializerProvider, beanDescription: BeanDescription,
            properties: MutableList<BeanPropertyDefinition>) {
        val introspector = context.annotationIntrospector!!
        val ignores = HashMap<KClass<*>, Boolean>()
        val iterator = properties.iterator()

        while (iterator.hasNext()) {
            val property = iterator.next()
            val accessor = property.accessor

            if (accessor == null) {
                iterator.remove()
                continue
            }

            val type = property.rawPrimaryType
            val result = ignores[type] ?: let {
                val config = context.config
                config.getConfigOverride(type).isIgnoredType ?: let {
                    val annotatedClass = context.introspectClassAnnotations(type)
                    introspector.isIgnorableType(config, annotatedClass) ?: false
                }
            }.also { ignores[type] = it }

            if (result) {
                iterator.remove()
            }
        }
    }

    /**
     * Helper method that will remove all properties that do not have a mutator.
     */
    protected open fun removeSetterlessGetters(config: SerializationConfig, beanDescription: BeanDescription,
            properties: MutableList<BeanPropertyDefinition>) {
        properties.removeIf { !it.couldDeserialize() && !it.isExplicitlyIncluded }
    }

    /**
     * Helper method called to ensure that we do not have "duplicate" type ids.
     */
    protected open fun removeOverlappingTypeIds(context: SerializerProvider, beanDescription: BeanDescription,
            builder: BeanSerializerBuilder,
            properties: MutableList<BeanPropertyWriter>): MutableList<BeanPropertyWriter> {
        for (property in properties) {
            val typeSerializer = property.typeSerializer ?: continue

            if (typeSerializer.typeInclusion != CirJsonTypeInfo.As.EXTERNAL_PROPERTY) {
                continue
            }

            val name = typeSerializer.propertyName
            val typePropertyName = PropertyName.construct(name)

            for (writer in properties) {
                if (writer !== property && writer.wouldConflictWithName(typePropertyName)) {
                    property.assignTypeSerializer(null)
                    break
                }
            }
        }

        return properties
    }

    /*
     *******************************************************************************************************************
     * Internal helper methods
     *******************************************************************************************************************
     */

    /**
     * Secondary helper method for constructing [BeanPropertyWriter] for given member (field or method).
     */
    protected open fun constructWriter(context: SerializerProvider, propertyDefinition: BeanPropertyDefinition,
            propertyBuilder: PropertyBuilder, staticTyping: Boolean, member: AnnotatedMember): BeanPropertyWriter {
        val name = propertyDefinition.fullName
        val type = member.type
        val property =
                BeanProperty.Standard(name, type, propertyDefinition.wrapperName, member, propertyDefinition.metadata)
        val annotatedSerializer = findSerializerFromAnnotation(context, member)?.let {
            it.resolve(context)
            context.handlePrimaryContextualization(it, property)
        }
        val contentTypeSerializer = type.takeIf { it.isContainerType || it.isReferenceType }
                ?.let { findPropertyContentTypeSerializer(context, it, member) }
        val typeSerializer = context.findPropertyTypeSerializer(type, member)
        return propertyBuilder.buildWriterInternal(context, propertyDefinition, type, annotatedSerializer,
                typeSerializer, contentTypeSerializer, member, staticTyping)
    }

    protected open fun findUnsupportedTypeSerializer(context: SerializerProvider, type: KotlinType,
            beanDescription: BeanDescription): ValueSerializer<*>? {
        val errorMessage = BeanUtil.checkUnsupportedType(type) ?: return null

        return if (context.config.findMixInClassFor(type.rawClass) == null) {
            UnsupportedTypeSerializer(type, errorMessage)
        } else {
            null
        }
    }

    /**
     * Helper method used for preventing attempts to serialize various CirJackson processor things which are not
     * generally serializable.
     */
    protected open fun isUnserializableCirJacksonType(context: SerializerProvider, type: KotlinType): Boolean {
        val raw = type.rawClass
        return ObjectMapper::class.isAssignableFrom(raw) || ObjectReader::class.isAssignableFrom(raw) ||
                ObjectWriter::class.isAssignableFrom(raw) || DatabindContext::class.isAssignableFrom(raw) ||
                TokenStreamFactory::class.isAssignableFrom(raw) || CirJsonParser::class.isAssignableFrom(raw) ||
                CirJsonGenerator::class.isAssignableFrom(raw)
    }

    companion object {

        /**
         * Like [BasicSerializerFactory], this factory is stateless, and thus a single shared global (a singleton)
         * instance can be used without thread-safety issues.
         */
        val INSTANCE = BeanSerializerFactory(null)

    }

}