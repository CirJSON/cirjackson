package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.configuration.GeneratorSettings
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.node.TreeBuildingGenerator
import org.cirjson.cirjackson.databind.util.createInstance
import org.cirjson.cirjackson.databind.util.exceptionMessage
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import org.cirjson.cirjackson.databind.util.isBogusClass
import java.util.*
import kotlin.reflect.KClass

/**
 * Extension over [SerializerProvider] that adds methods needed by [ObjectMapper] (and [ObjectWriter]) but that are not
 * to be exposed as general context during serialization.
 * 
 * Also note that all custom [SerializerProvider] implementations must subclass this class: [ObjectMapper] requires this
 * type, not basic provider type.
 */
open class SerializationContextExtended protected constructor(streamFactory: TokenStreamFactory,
        config: SerializationConfig, generatorConfig: GeneratorSettings, factory: SerializerFactory,
        serializerCache: SerializerCache) :
        SerializerProvider(streamFactory, config, generatorConfig, factory, serializerCache) {

    /*
     *******************************************************************************************************************
     * Additional state
     *******************************************************************************************************************
     */

    /**
     * Per-serialization map Object Ids that have seen so far.
     */
    @Transient
    protected var mySeenObjectIds: MutableMap<Any, WritableObjectId>? = null

    @Transient
    protected var myObjectIdGenerators: ArrayList<ObjectIdGenerator<*>>? = null

    /*
     *******************************************************************************************************************
     * Abstract method implementations, factory methods
     *******************************************************************************************************************
     */

    override fun serializerInstance(annotated: Annotated, serializerDefinition: Any?): ValueSerializer<Any>? {
        serializerDefinition ?: return null

        if (serializerDefinition is ValueSerializer<*>) {
            return handleResolvable(serializerDefinition)
        }

        if (serializerDefinition !is KClass<*>) {
            return reportBadDefinition(annotated.type,
                    "AnnotationIntrospector returned serializer definition of type ${serializerDefinition::class.qualifiedName}; expected type `ValueSerializer` or `KClass<ValueSerializer>` instead")
        }

        if (serializerDefinition == ValueSerializer.None::class || serializerDefinition.isBogusClass) {
            return null
        }

        if (!ValueSerializer::class.isAssignableFrom(serializerDefinition)) {
            return reportBadDefinition(annotated.type,
                    "AnnotationIntrospector returned KClass `${serializerDefinition.qualifiedName}`; expected `KClass<ValueSerializer>`")
        }

        val serializer = config.handlerInstantiator?.serializerInstance(config, annotated, serializerDefinition)
                ?: serializerDefinition.createInstance(config.canOverrideAccessModifiers()) as ValueSerializer<*>
        return handleResolvable(serializer)
    }

    override fun includeFilterInstance(forProperty: BeanPropertyDefinition?, filterClass: KClass<*>?): Any? {
        filterClass ?: return null
        return config.handlerInstantiator?.includeFilterInstance(config, forProperty, filterClass)
                ?: filterClass.createInstance(config.canOverrideAccessModifiers())
    }

    @Suppress("SENSELESS_COMPARISON")
    override fun includeFilterSuppressNulls(filter: Any?): Boolean {
        filter ?: return true

        return try {
            filter == null
        } catch (e: Exception) {
            val message =
                    "Problem determining whether filter of type '${filter::class.qualifiedName}' should filter out `null` values: (${e::class.qualifiedName}) ${e.exceptionMessage()}"
            reportBadDefinition(filter::class, message, e)
        }
    }

    /*
     *******************************************************************************************************************
     * Abstract method implementations, serialization-like methods
     *******************************************************************************************************************
     */

    /**
     * Method that will convert given Java value (usually bean) into its equivalent Tree model [CirJsonNode]
     * representation. Functionally similar to serializing value into token stream and parsing that stream back as tree
     * model node, but more efficient as [TokenBuffer][org.cirjson.cirjackson.databind.util.TokenBuffer] is used to
     * contain the intermediate representation instead of fully serialized contents.
     * 
     * NOTE: while results are usually identical to that of serialization followed by deserialization, this is not
     * always the case. In some cases serialization into intermediate representation will retain encapsulation of things
     * like raw value ([org.cirjson.cirjackson.databind.util.RawValue]) or basic node identity ([CirJsonNode]). If so,
     * result is a valid tree, but values are not re-constructed through actual format representation. So if
     * transformation requires actual materialization of encoded content, it will be necessary to do actual
     * serialization.
     *
     * @param T Actual node type; usually either basic [CirJsonNode] or
     * [org.cirjson.cirjackson.databind.node.ObjectNode]
     * 
     * @param fromValue Value to convert
     *
     * @return (non-`null`) Root node of the resulting content tree: in case of `null` value node for which
     * [CirJsonNode.isNull] returns `true`.
     */
    @Throws(CirJacksonException::class)
    @Suppress("UNCHECKED_CAST")
    override fun <T : CirJsonNode> valueToTree(fromValue: Any?): T {
        val nodeFactory = config.nodeFactory

        fromValue ?: return nodeFactory.nullNode() as T

        TreeBuildingGenerator.forSerialization(this, nodeFactory).use {
            val rawType = fromValue::class
            val serializer = findTypedValueSerializer(rawType, true)

            val rootName = config.fullRootName

            if (rootName == null) {
                if (config.isEnabled(SerializationFeature.WRAP_ROOT_VALUE)) {
                    serialize(it, fromValue, serializer, findRootName(rawType))
                } else {
                    serialize(it, fromValue, serializer)
                }
            } else if (!rootName.isEmpty()) {
                serialize(it, fromValue, serializer, rootName)
            } else {
                serialize(it, fromValue, serializer)
            }

            return it.treeBuilt()!! as T
        }
    }

    /*
     *******************************************************************************************************************
     * Object ID handling
     *******************************************************************************************************************
     */

    override fun findObjectId(forPojo: Any, generatorType: ObjectIdGenerator<*>): WritableObjectId {
        val seen = mySeenObjectIds?.also { return it[forPojo] ?: return@also }
                ?: createObjectIdMap().also { mySeenObjectIds = it }

        var generator: ObjectIdGenerator<*>? = null

        val generators = myObjectIdGenerators?.also {
            for (gen in it) {
                if (gen.canUseFor(generatorType)) {
                    generator = gen
                    break
                }
            }
        } ?: ArrayList<ObjectIdGenerator<*>>(8).also { myObjectIdGenerators = it }

        if (generator == null) {
            generator = generatorType.newForSerialization(this)
            generators.add(generator)
        }

        return WritableObjectId(generator).also { seen[forPojo] = it }
    }

    /**
     * Overridable helper method used for creating [MutableMap] used for storing mappings from serializable objects to
     * their Object IDs.
     */
    protected open fun createObjectIdMap(): MutableMap<Any, WritableObjectId> {
        return if (isEnabled(SerializationFeature.USE_EQUALITY_FOR_OBJECT_ID)) {
            HashMap()
        } else {
            IdentityHashMap()
        }
    }

    /*
     *******************************************************************************************************************
     * Extended API: simple accessors
     *******************************************************************************************************************
     */

    /**
     * Accessor for the [CirJsonGenerator] currently in use for serializing content. `null` for blueprint instances;
     * non-`null` for actual active provider instances.
     */
    override val generator: CirJsonGenerator?
        get() = myGenerator

    /*
     *******************************************************************************************************************
     * Extended API called by ObjectMapper: value serialization
     *******************************************************************************************************************
     */

    /**
     * The method to be called by [ObjectMapper] and [ObjectWriter] for serializing given value, using serializers that
     * this provider has access to (via caching and/or creating new serializers as need be).
     */
    @Throws(CirJacksonException::class)
    open fun serializeValue(generator: CirJsonGenerator, value: Any?) {
        assignGenerator(generator)

        if (value == null) {
            serializeNull(generator)
            return
        }

        val type = value::class
        val serializer = findTypedValueSerializer(type, true)
        val rootName = config.fullRootName

        if (rootName == null) {
            if (config.isEnabled(SerializationFeature.WRAP_ROOT_VALUE)) {
                serialize(generator, value, serializer, findRootName(type))
                return
            }
        } else if (!rootName.isEmpty()) {
            serialize(generator, value, serializer, rootName)
            return
        }

        serialize(generator, value, serializer)
    }

    /**
     * The method to be called by [ObjectMapper] and [ObjectWriter] for serializing given value, using serializers that
     * this provider has access to (via caching and/or creating new serializers as need be). The given value is assumed
     * to be of specified root type, instead of runtime type of value
     *
     * @param rootType Type to use for locating serializer to use, instead of actual runtime type. Must be actual type,
     * or one of its supertypes
     */
    @Throws(CirJacksonException::class)
    open fun serializeValue(generator: CirJsonGenerator, value: Any?, rootType: KotlinType) {
        assignGenerator(generator)

        if (value == null) {
            serializeNull(generator)
            return
        }

        if (!rootType.rawClass.isAssignableFrom(value::class)) {
            reportIncompatibleRootType(value, rootType)
        }

        val serializer = findTypedValueSerializer(rootType, true)
        val rootName = config.fullRootName

        if (rootName == null) {
            if (config.isEnabled(SerializationFeature.WRAP_ROOT_VALUE)) {
                serialize(generator, value, serializer, findRootName(rootType))
                return
            }
        } else if (!rootName.isEmpty()) {
            serialize(generator, value, serializer, rootName)
            return
        }

        serialize(generator, value, serializer)
    }

    /**
     * The method to be called by [ObjectWriter] for serializing given value (assumed to be of specified root type,
     * instead of runtime type of value), when it may know specific [ValueSerializer] to use.
     *
     * @param rootType Type to use for locating serializer to use, instead of actual runtime type, if no serializer is
     * passed
     * 
     * @param serializer Root Serializer to use, if not `null`
     */
    @Throws(CirJacksonException::class)
    open fun serializeValue(generator: CirJsonGenerator, value: Any?, rootType: KotlinType?,
            serializer: ValueSerializer<Any>?) {
        assignGenerator(generator)

        if (value == null) {
            serializeNull(generator)
            return
        }

        if (rootType != null && !rootType.rawClass.isAssignableFrom(value::class)) {
            reportIncompatibleRootType(value, rootType)
        }

        val realSerializer = serializer ?: findTypedValueSerializer(rootType!!, true)
        var rootName = config.fullRootName

        if (rootName == null) {
            if (config.isEnabled(SerializationFeature.WRAP_ROOT_VALUE)) {
                rootName = rootType?.let { findRootName(it) } ?: findRootName(value::class)
                serialize(generator, value, realSerializer, rootName)
                return
            }
        } else if (!rootName.isEmpty()) {
            serialize(generator, value, realSerializer, rootName)
            return
        }

        serialize(generator, value, realSerializer)
    }

    /**
     * Alternate serialization call used for polymorphic types, when [TypeSerializer] is already known, but the actual
     * serializer may or may not be.
     */
    @Throws(CirJacksonException::class)
    open fun serializePolymorphic(generator: CirJsonGenerator, value: Any?, rootType: KotlinType?,
            serializer: ValueSerializer<Any>?, typeSerializer: TypeSerializer) {
        assignGenerator(generator)

        if (value == null) {
            serializeNull(generator)
            return
        }

        if (rootType != null && !rootType.rawClass.isAssignableFrom(value::class)) {
            reportIncompatibleRootType(value, rootType)
        }

        val realSerializer = serializer ?: rootType?.takeIf { it.isContainerType }
                ?.let { handleRootContextualization(findValueSerializer(it))!! } ?: handleRootContextualization(
                findValueSerializer(value::class))!!
        val rootName = config.fullRootName

        val wrap = if (rootName == null) {
            config.isEnabled(SerializationFeature.WRAP_ROOT_VALUE).also {
                if (!it) {
                    return@also
                }

                generator.writeStartObject()
                val propertyName = findRootName(value::class)
                generator.writeName(propertyName.simpleAsEncoded(config))
            }
        } else if (rootName.isEmpty()) {
            false
        } else {
            generator.writeStartObject()
            generator.writeName(rootName.simpleName)
            true
        }

        realSerializer.serializeWithType(value, generator, this, typeSerializer)

        if (wrap) {
            generator.writeEndObject()
        }
    }

    @Throws(CirJacksonException::class)
    private fun serialize(generator: CirJsonGenerator, value: Any, serializer: ValueSerializer<Any>,
            rootName: PropertyName) {
        generator.writeStartObject()
        generator.writeName(rootName.simpleAsEncoded(config))
        serializer.serialize(value, generator, this)
        generator.writeEndObject()
    }

    @Throws(CirJacksonException::class)
    private fun serialize(generator: CirJsonGenerator, value: Any, serializer: ValueSerializer<Any>) {
        serializer.serialize(value, generator, this)
    }

    /**
     * Helper method called when root value to serialize is `null`
     */
    @Throws(CirJacksonException::class)
    protected open fun serializeNull(generator: CirJsonGenerator) {
        defaultNullValueSerializer.serializeNullable(null, generator, this)
    }

    /*
     *******************************************************************************************************************
     * Extended API called by ObjectMapper: other
     *******************************************************************************************************************
     */

    /**
     * The method to be called by [ObjectMapper] and [ObjectWriter] to expose the format of the given type to the given
     * visitor
     *
     * @param type The type for which to generate format
     *
     * @param visitor the visitor to accept the format
     */
    open fun acceptCirJsonFormatVisitor(type: KotlinType, visitor: CirJsonFormatVisitorWrapper) {
        visitor.provider = this
        findRootValueSerializer(type).acceptCirJsonFormatVisitor(visitor, type)
    }

    /*
     *******************************************************************************************************************
     * Other helper methods
     *******************************************************************************************************************
     */

    private fun assignGenerator(generator: CirJsonGenerator) {
        myGenerator = generator
        myWriteCapabilities = generator.streamWriteCapabilities()
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Concrete implementation defined separately so it can be declared `final`. Alternate implements should instead
     * just extend [SerializationContextExtended]
     */
    class Implementation(streamFactory: TokenStreamFactory, config: SerializationConfig,
            generatorConfig: GeneratorSettings, factory: SerializerFactory, serializerCache: SerializerCache) :
            SerializationContextExtended(streamFactory, config, generatorConfig, factory, serializerCache)

}