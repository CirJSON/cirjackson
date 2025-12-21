package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.deserialization.standard.StandardConvertingDeserializer
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.type.*
import org.cirjson.cirjackson.databind.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

/**
 * Class that defines caching layer between callers (like [ObjectMapper], [DeserializationContext]) and classes that
 * construct deserializers ([DeserializerFactory]).
 * 
 * @property myCachedDeserializers We will also cache some dynamically constructed deserializers; specifically, ones
 * that are expensive to construct. This currently means POJO, Enum and Container (collection, map) deserializers.
 */
@Suppress("SameParameterValue")
class DeserializerCache(private val myCachedDeserializers: LookupCache<KotlinType, ValueDeserializer<Any>>) {

    /*
     *******************************************************************************************************************
     * Caching
     *******************************************************************************************************************
     */

    /**
     * During deserializer construction process we may need to keep track of partially completed deserializers, to
     * resolve cyclic dependencies. This is the map used for storing deserializers before they are fully complete.
     */
    private val myIncompleteDeserializers = HashMap<KotlinType, ValueDeserializer<Any>>(0)

    /**
     * We hold an explicit lock while creating deserializers to avoid creating duplicates. Guards
     * [myIncompleteDeserializers].
     */
    private val myIncompleteDeserializersLock = ReentrantLock()

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor() : this(SimpleLookupCache(64, DEFAULT_MAX_CACHE_SIZE))

    fun emptyCopy(): DeserializerCache {
        return DeserializerCache(myCachedDeserializers.emptyCopy())
    }

    /*
     *******************************************************************************************************************
     * Access to caching aspects
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to determine how many deserializers this provider is caching currently (if it does
     * caching: default implementation does) Exact count depends on what kind of deserializers get cached; default
     * implementation caches only dynamically constructed deserializers, but not eagerly constructed standard
     * deserializers (which is different from how serializer provider works).
     * 
     * The main use case for this method is to allow conditional flushing of deserializer cache, if certain number of
     * entries is reached.
     */
    fun cachedDeserializersCount(): Int {
        return myCachedDeserializers.size
    }

    /**
     * Method that will drop all dynamically constructed deserializers (ones that are counted as result value for
     * [cachedDeserializersCount]). This can be used to remove memory usage (in case some deserializers are only used
     * once or so), or to force re-construction of deserializers after configuration changes for mapper than owns the
     * provider.
     */
    fun flushCachedDeserializers() {
        myCachedDeserializers.clear()
    }

    /*
     *******************************************************************************************************************
     * General deserializer locating method
     *******************************************************************************************************************
     */

    /**
     * Method called to get hold of a deserializer for a value of given type; or if no such deserializer can be found, a
     * default handler (which may do a best-effort generic serialization or just simply throw an exception when
     * invoked).
     * 
     * Note: this method is only called for value types; not for keys. Key deserializers can be accessed using
     * [findKeyDeserializer].
     * 
     * Note also that deserializer returned is guaranteed to be resolved (see [ValueDeserializer.resolve]), but not
     * contextualized (wrt [ValueDeserializer.createContextual]): caller has to handle latter if necessary.
     *
     * @param context Deserialization context
     * 
     * @param propertyType Declared type of the value to deserializer (obtained using 'setter' method signature and/or
     * type annotations
     */
    fun findValueDeserializer(context: DeserializationContext, factory: DeserializerFactory,
            propertyType: KotlinType): ValueDeserializer<Any> {
        return findCachedDeserializer(propertyType) ?: createAndCacheValueDeserializer(context, factory, propertyType)
        ?: handleUnknownValueDeserializer(context, propertyType)
    }

    /**
     * Method called to get hold of a deserializer to use for deserializing keys for [Map].
     *
     * @throws DatabindException if there are fatal problems with accessing suitable key deserializer; including that of
     * not finding any serializer
     */
    fun findKeyDeserializer(context: DeserializationContext, factory: DeserializerFactory,
            type: KotlinType): KeyDeserializer {
        val keyDeserializer =
                factory.createKeyDeserializer(context, type) ?: return handleUnknownKeyDeserializer(context, type)

        keyDeserializer.resolve(context)
        return keyDeserializer
    }

    /*
     *******************************************************************************************************************
     * Helper methods that handle cache lookups
     *******************************************************************************************************************
     */

    private fun findCachedDeserializer(type: KotlinType): ValueDeserializer<Any>? {
        if (hasCustomHandlers(type)) {
            return null
        }

        return myCachedDeserializers[type]
    }

    /**
     * Method that will try to create a deserializer for given type, and resolve and cache it if necessary
     *
     * @param context Currently active deserialization context
     * 
     * @param type Type of property to deserialize (never `null`, callers verify)
     */
    private fun createAndCacheValueDeserializer(context: DeserializationContext, factory: DeserializerFactory,
            type: KotlinType): ValueDeserializer<Any>? {
        val isCustom = hasCustomHandlers(type)

        if (!isCustom) {
            val deserializer = myCachedDeserializers[type]

            if (deserializer != null) {
                return deserializer
            }
        }

        myIncompleteDeserializersLock.withLock {
            if (!isCustom) {
                val deserializer = myCachedDeserializers[type]

                if (deserializer != null) {
                    return deserializer
                }
            }

            val count = myIncompleteDeserializers.size

            if (count > 0) {
                val deserializer = myIncompleteDeserializers[type]

                if (deserializer != null) {
                    return deserializer
                }
            }

            try {
                return createAndCache(context, factory, type, isCustom)
            } finally {
                if (count == 0 && myIncompleteDeserializers.isNotEmpty()) {
                    myIncompleteDeserializers.clear()
                }
            }
        }
    }

    /**
     * Method that handles actual construction (via factory) and caching (both intermediate and eventual)
     */
    private fun createAndCache(context: DeserializationContext, factory: DeserializerFactory, type: KotlinType,
            isCustom: Boolean): ValueDeserializer<Any>? {
        val deserializer = try {
            createDeserializer(context, factory, type)
        } catch (e: IllegalArgumentException) {
            context.reportBadDefinition(type, e.exceptionMessage()!!)
        }

        deserializer ?: return null

        val addToCache = !isCustom && deserializer.isCacheable

        myIncompleteDeserializers[type] = deserializer

        try {
            deserializer.resolve(context)
        } finally {
            myIncompleteDeserializers.remove(type)
        }

        if (addToCache) {
            myCachedDeserializers[type] = deserializer
        }

        return deserializer
    }

    /*
     *******************************************************************************************************************
     * Helper methods for actual construction of deserializers
     *******************************************************************************************************************
     */

    /**
     * Method that does the heavy lifting of checking for per-type annotations, find out full type, and figure out which
     * actual factory method to call.
     */
    @Suppress("UNCHECKED_CAST")
    private fun createDeserializer(context: DeserializationContext, factory: DeserializerFactory,
            type: KotlinType): ValueDeserializer<Any>? {
        var realType = type
        val config = context.config

        if (realType.isAbstract || realType.isMapLikeType || realType.isCollectionLikeType) {
            realType = config.mapAbstractType(realType)
        }

        var beanDescription = context.introspectBeanDescription(realType)
        val deserializer = findDeserializerFromAnnotation(context, beanDescription.classInfo)

        if (deserializer != null) {
            return deserializer
        }

        val newType = modifyTypeByAnnotation(context, beanDescription.classInfo, realType)

        if (newType !== realType) {
            realType = newType
            beanDescription = context.introspectBeanDescription(newType)
        }

        val builder = beanDescription.findPOJOBuilder()

        if (builder != null) {
            return factory.createBuilderBasedDeserializer(context, realType, beanDescription, builder)
        }

        val converter =
                beanDescription.findDeserializationConverter() ?: return createDeserializer(context, factory, realType,
                        beanDescription) as ValueDeserializer<Any>?

        val delegateType = converter.getInputType(context.typeFactory)

        if (!delegateType.hasRawClass(realType.rawClass)) {
            beanDescription = context.introspectBeanDescription(delegateType)
        }

        return StandardConvertingDeserializer(converter, delegateType,
                createDeserializer(context, factory, realType, beanDescription) as ValueDeserializer<Any>)
    }

    private fun createDeserializer(context: DeserializationContext, factory: DeserializerFactory, type: KotlinType,
            beanDescription: BeanDescription): ValueDeserializer<*>? {
        val config = context.config

        if (type.isEnumType) {
            return factory.createEnumDeserializer(context, type, beanDescription)
        }

        if (type.isContainerType) {
            if (type.isArrayType) {
                return factory.createArrayDeserializer(context, type as ArrayType, beanDescription)
            }

            if (type.isMapLikeType) {
                val format = beanDescription.findExpectedFormat(type.rawClass)!!

                if (format.shape != CirJsonFormat.Shape.POJO) {
                    return if (type is MapType) {
                        factory.createMapDeserializer(context, type, beanDescription)
                    } else {
                        factory.createMapLikeDeserializer(context, type as MapLikeType, beanDescription)
                    }
                }
            }

            if (type.isCollectionLikeType) {
                val format = beanDescription.findExpectedFormat(type.rawClass)!!

                if (format.shape != CirJsonFormat.Shape.POJO) {
                    return if (type is CollectionType) {
                        factory.createCollectionDeserializer(context, type, beanDescription)
                    } else {
                        factory.createCollectionLikeDeserializer(context, type as CollectionLikeType, beanDescription)
                    }
                }
            }
        }

        return if (type.isReferenceType) {
            factory.createReferenceDeserializer(context, type as ReferenceType, beanDescription)
        } else if (CirJsonNode::class.isAssignableFrom(type.rawClass)) {
            factory.createTreeDeserializer(config, type, beanDescription)
        } else {
            factory.createBeanDeserializer(context, type, beanDescription)
        }
    }

    /**
     * Helper method called to check if a class or method has annotation that tells which class to use for
     * deserialization. Returns `null` if no such annotation found.
     */
    private fun findDeserializerFromAnnotation(context: DeserializationContext,
            annotated: Annotated): ValueDeserializer<Any>? {
        val deserializerDefinition =
                context.annotationIntrospector!!.findDeserializer(context.config, annotated) ?: return null
        val deserializer = context.deserializerInstance(annotated, deserializerDefinition)
        return findConvertingDeserializer(context, annotated, deserializer)
    }

    /**
     * Helper method that will check whether given annotated entity (usually class, but may also be a property accessor)
     * indicates that a [Converter] is to be used; and if so, to construct and return suitable serializer for it. If
     * not, will simply return given deserializer as is.
     */
    private fun findConvertingDeserializer(context: DeserializationContext, annotated: Annotated,
            deserializer: ValueDeserializer<Any>?): ValueDeserializer<Any>? {
        val converter = findConverter(context, annotated) ?: return deserializer
        val delegateType = converter.getInputType(context.typeFactory)
        return StandardConvertingDeserializer(converter, delegateType, deserializer)
    }

    private fun findConverter(context: DeserializationContext, annotated: Annotated): Converter<Any, Any>? {
        val converterDefinition =
                context.annotationIntrospector!!.findDeserializationConverter(context.config, annotated) ?: return null
        return context.converterInstance(annotated, converterDefinition)
    }

    /**
     * Method called to see if given method has annotations that indicate a more specific type than what the argument
     * specifies. If annotations are present, they must specify compatible Class; instance of which can be assigned
     * using the method. This means that the Class has to be raw class of type, or its subclass (or, implementing class
     * if original Class instance is an interface).
     *
     * @param context Currently active deserialization context
     *
     * @param annotated Method or field that the type is associated with
     *
     * @param type Type derived from the setter argument
     *
     * @return Original type if no annotations are present; or a more specific type derived from it if type
     * annotation(s) was found
     */
    private fun modifyTypeByAnnotation(context: DeserializationContext, annotated: Annotated,
            type: KotlinType): KotlinType {
        var realType = type
        val introspector = context.annotationIntrospector ?: return realType
        val config = context.config

        if (realType.isMapLikeType) {
            val keyType = realType.keyType

            if (keyType != null && keyType.valueHandler == null) {
                val keyDeserializerDefinition = introspector.findKeyDeserializer(config, annotated)

                if (keyDeserializerDefinition != null) {
                    val keyDeserializer = context.keyDeserializerInstance(annotated, keyDeserializerDefinition)

                    if (keyDeserializer != null) {
                        realType = (realType as MapLikeType).withKeyValueHandler(keyDeserializer)
                    }
                }
            }
        }

        val contentType =
                realType.contentType ?: return introspector.refineDeserializationType(config, annotated, realType)

        if (contentType.valueHandler != null) {
            return introspector.refineDeserializationType(config, annotated, realType)
        }

        val contentDeserializerDefinition = introspector.findContentDeserializer(config, annotated)
                ?: return introspector.refineDeserializationType(config, annotated, realType)

        val contentDeserializer: ValueDeserializer<*>? = if (contentDeserializerDefinition is ValueDeserializer<*>) {
            contentDeserializerDefinition
        } else {
            val contentDeserializerClass = verifyAsClass(contentDeserializerDefinition, "findContentDeserializer",
                    ValueDeserializer.None::class)

            if (contentDeserializerClass != null) {
                context.deserializerInstance(annotated, contentDeserializerClass)
            } else {
                null
            }
        }

        if (contentDeserializer != null) {
            realType = realType.withContentValueHandler(contentDeserializer)
        }

        return introspector.refineDeserializationType(config, annotated, realType)
    }

    /*
     *******************************************************************************************************************
     * Helper methods, other
     *******************************************************************************************************************
     */

    /**
     * Helper method used to prevent both caching and cache lookups for structured types that have custom value handlers
     */
    private fun hasCustomHandlers(type: KotlinType): Boolean {
        if (!type.isContainerType) {
            return false
        }

        val contentType = type.contentType

        if (contentType?.valueHandler != null || contentType?.typeHandler != null) {
            return true
        }

        if (!type.isMapLikeType) {
            return false
        }

        return type.keyType!!.valueHandler != null
    }

    private fun verifyAsClass(src: Any?, methodName: String, noneClass: KClass<*>): KClass<*>? {
        src ?: return null

        if (src !is KClass<*>) {
            throw IllegalStateException(
                    "AnnotationIntrospector.$methodName() returned value of type ${src::class.qualifiedName}: expected type `ValueSerializer` or `KClass<ValueSerializer>` instead")
        }

        if (src == noneClass || src.isBogusClass) {
            return null
        }

        return src
    }

    /*
     *******************************************************************************************************************
     * Error reporting methods
     *******************************************************************************************************************
     */

    private fun handleUnknownValueDeserializer(context: DeserializationContext,
            type: KotlinType): ValueDeserializer<Any> {
        val rawClass = type.rawClass

        return if (!rawClass.isConcrete) {
            context.reportBadDefinition(type, "Cannot find a Value deserializer for abstract type $type")
        } else {
            context.reportBadDefinition(type, "Cannot find a Value deserializer for type $type")
        }
    }

    private fun handleUnknownKeyDeserializer(context: DeserializationContext, type: KotlinType): KeyDeserializer {
        return context.reportBadDefinition(type, "Cannot find a (Map) Key deserializer for type $type")
    }

    companion object {

        /**
         * Default size of the underlying cache to use.
         */
        const val DEFAULT_MAX_CACHE_SIZE = 1000

    }

}