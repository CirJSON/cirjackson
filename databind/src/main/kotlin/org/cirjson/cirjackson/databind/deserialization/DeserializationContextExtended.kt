package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdResolver
import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.util.*
import kotlin.reflect.KClass

/**
 * Complete [DeserializationContext] implementation that adds extended API for [ObjectMapper] (and [ObjectReader]) to
 * call, as well as implements certain parts that base class has left abstract.
 * 
 * @constructor Constructor that will pass specified deserializer factory and cache: they cannot be `null`.
 */
abstract class DeserializationContextExtended protected constructor(tokenStreamFactory: TokenStreamFactory,
        deserializerFactory: DeserializerFactory, cache: DeserializerCache, config: DeserializationConfig,
        schema: FormatSchema?, values: InjectableValues?) :
        DeserializationContext(tokenStreamFactory, deserializerFactory, cache, config, schema, values) {

    @Transient
    protected var myObjectIds: LinkedHashMap<ObjectIdGenerator.IDKey, ReadableObjectId>? = null

    private var myObjectIdResolvers: MutableList<ObjectIdResolver>? = null

    open fun assignParser(parser: CirJsonParser): DeserializationContextExtended {
        myParser = parser
        myReadCapabilities = parser.streamReadCapabilities()
        return this
    }

    open fun assignAndReturnParser(parser: CirJsonParser): CirJsonParser {
        myParser = parser
        myReadCapabilities = parser.streamReadCapabilities()
        return parser
    }

    /*
     *******************************************************************************************************************
     * Abstract methods implementations, Object ID
     *******************************************************************************************************************
     */

    override fun findObjectId(id: Any, generator: ObjectIdGenerator<*>, resolver: ObjectIdResolver): ReadableObjectId {
        val key = generator.key(id)!!

        val objectIds = myObjectIds?.also { return it[key] ?: return@also }
                ?: LinkedHashMap<ObjectIdGenerator.IDKey, ReadableObjectId>().also { myObjectIds = it }

        var objectIdResolver: ObjectIdResolver? = null

        val resolvers = myObjectIdResolvers?.also {
            for (idResolver in it) {
                if (idResolver.canUseFor(resolver)) {
                    objectIdResolver = idResolver
                    break
                }
            }
        } ?: ArrayList<ObjectIdResolver>(8).also { myObjectIdResolvers = it }

        if (objectIdResolver == null) {
            objectIdResolver = resolver.newForDeserialization(this)
            resolvers.add(objectIdResolver)
        }

        val entry = createReadableObjectId(key)
        entry.resolver = objectIdResolver
        objectIds[key] = entry
        return entry
    }

    /**
     * Overridable factory method to create a new instance of ReadableObjectId or its subclass. It is meant to be
     * overridden when custom ReadableObjectId is needed for [tryToResolveUnresolvedObjectId]. Default implementation
     * simply constructs default [ReadableObjectId] with given `key`.
     *
     * @param key The key to associate with the new ReadableObjectId
     * 
     * @return New ReadableObjectId instance
     */
    protected open fun createReadableObjectId(key: ObjectIdGenerator.IDKey): ReadableObjectId {
        return ReadableObjectId(key)
    }

    @Throws(UnresolvedForwardReferenceException::class)
    override fun checkUnresolvedObjectId() {
        val objectIds = myObjectIds ?: return

        if (!isEnabled(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS)) {
            return
        }

        var exception: UnresolvedForwardReferenceException? = null

        for ((_, readableObjectId) in objectIds) {
            if (!readableObjectId.hasReferringProperties()) {
                continue
            }

            if (tryToResolveUnresolvedObjectId(readableObjectId)) {
                continue
            }

            if (exception == null) {
                exception = UnresolvedForwardReferenceException(parser,
                        "Unresolved forward references for: ").withStackTrace()
            }

            val key = readableObjectId.key.key

            for (referring in readableObjectId.referringProperties()) {
                exception.addUnresolvedId(key, referring.beanType, referring.location)
            }
        }

        exception?.let { throw it }
    }

    /**
     * Overridable helper method called to try to resolve otherwise unresolvable [ReadableObjectId];
     * and if this succeeds, return `true` to indicate problem has been resolved in
     * some way, so that caller can avoid reporting it as an error.
     *<p>
     * Default implementation simply calls [ReadableObjectId.tryToResolveUnresolved] and
     * returns whatever it returns.
     */
    protected open fun tryToResolveUnresolvedObjectId(readableObjectId: ReadableObjectId): Boolean {
        return readableObjectId.tryToResolveUnresolved(this)
    }

    /*
     *******************************************************************************************************************
     * Abstract methods implementations, other factory methods
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    override fun deserializerInstance(annotated: Annotated, deserializerDefinition: Any?): ValueDeserializer<Any>? {
        deserializerDefinition ?: return null

        val deserializer: ValueDeserializer<*> = deserializerDefinition as? ValueDeserializer<*> ?: let {
            if (deserializerDefinition !is KClass<*>) {
                throw IllegalStateException(
                        "AnnotationIntrospector returned deserializer definition of type ${deserializerDefinition::class.qualifiedName}; expected type `ValueDeserializer` or `KClass<ValueDeserializer>` instead")
            }

            if (deserializerDefinition == ValueDeserializer.None::class || deserializerDefinition.isBogusClass) {
                return null
            }

            if (!ValueDeserializer::class.isAssignableFrom(deserializerDefinition)) {
                throw IllegalStateException(
                        "AnnotationIntrospector returned `KClass<${deserializerDefinition.qualifiedName}>`; expected `KClass<ValueDeserializer>`")
            }

            myConfig.handlerInstantiator?.deserializerInstance(myConfig, annotated, deserializerDefinition)
                    ?: deserializerDefinition.createInstance(
                            myConfig.canOverrideAccessModifiers())!! as ValueDeserializer<*>
        }

        deserializer.resolve(this)
        return deserializer as ValueDeserializer<Any>
    }

    override fun keyDeserializerInstance(annotated: Annotated, deserializerDefinition: Any?): KeyDeserializer? {
        deserializerDefinition ?: return null

        val deserializer = deserializerDefinition as? KeyDeserializer ?: let {
            if (deserializerDefinition !is KClass<*>) {
                throw IllegalStateException(
                        "AnnotationIntrospector returned key deserializer definition of type ${deserializerDefinition::class.qualifiedName}; expected type `KeyDeserializer` or `KClass<KeyDeserializer>` instead")
            }

            if (deserializerDefinition == KeyDeserializer.None::class || deserializerDefinition.isBogusClass) {
                return null
            }

            if (!KeyDeserializer::class.isAssignableFrom(deserializerDefinition)) {
                throw IllegalStateException(
                        "AnnotationIntrospector returned `KClass<${deserializerDefinition.qualifiedName}>`; expected `KClass<KeyDeserializer>`")
            }

            myConfig.handlerInstantiator?.keyDeserializerInstance(myConfig, annotated, deserializerDefinition)
                    ?: deserializerDefinition.createInstance(myConfig.canOverrideAccessModifiers())!! as KeyDeserializer
        }

        deserializer.resolve(this)
        return deserializer
    }

    /*
     *******************************************************************************************************************
     * Extended API, read methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    open fun readRootValue(parser: CirJsonParser, valueType: KotlinType, deserializer: ValueDeserializer<Any>,
            valueToUpdate: Any?): Any? {
        return if (myConfig.useRootWrapping()) {
            unwrapAndDeserialize(parser, valueType, deserializer, valueToUpdate)
        } else if (valueToUpdate == null) {
            deserializer.deserialize(parser, this)
        } else {
            deserializer.deserialize(parser, this, valueToUpdate)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun unwrapAndDeserialize(parser: CirJsonParser, rootType: KotlinType,
            deserializer: ValueDeserializer<Any>, valueToUpdate: Any?): Any? {
        val expectedRootName = findRootName(rootType)
        val expectedSimpleName = expectedRootName.simpleName

        if (parser.currentToken() != CirJsonToken.START_OBJECT) {
            return reportWrongTokenException(rootType, CirJsonToken.START_OBJECT,
                    "Current token not `CirJsonToken.START_OBJECT` (needed to unwrap root name ${expectedSimpleName.name()}), but ${parser.currentToken()}")
        }

        if (parser.nextToken() != CirJsonToken.PROPERTY_NAME) {
            return reportWrongTokenException(rootType, CirJsonToken.START_OBJECT,
                    "Current token not `CirJsonToken.PROPERTY_NAME` (needed to unwrap root name ${expectedSimpleName.name()}), but ${parser.currentToken()}")
        }

        val actualName = parser.currentName()

        if (expectedSimpleName != actualName) {
            return reportPropertyInputMismatch(rootType, actualName,
                    "Root name (${actualName.name()}) does not match expected (${expectedSimpleName.name()}) for type ${rootType.typeDescription}")
        }

        parser.nextToken()

        val result = if (valueToUpdate == null) {
            deserializer.deserialize(parser, this)
        } else {
            deserializer.deserialize(parser, this, valueToUpdate)
        }

        if (parser.nextToken() != CirJsonToken.END_OBJECT) {
            return reportWrongTokenException(rootType, CirJsonToken.END_OBJECT,
                    "Current token not `CirJsonToken.END_OBJECT` (needed to unwrap root name ${expectedSimpleName.name()}), but ${parser.currentToken()}")
        }

        return result
    }

    /*
     *******************************************************************************************************************
     * Concrete implementation class
     *******************************************************************************************************************
     */

    /**
     * Actual full concrete implementation
     */
    class Implementation(tokenStreamFactory: TokenStreamFactory, deserializerFactory: DeserializerFactory,
            cache: DeserializerCache, config: DeserializationConfig, schema: FormatSchema?, values: InjectableValues?) :
            DeserializationContextExtended(tokenStreamFactory, deserializerFactory, cache, config, schema, values)

}