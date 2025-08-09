package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.StreamReadFeature
import org.cirjson.cirjackson.core.StreamWriteFeature
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.deserialization.*
import org.cirjson.cirjackson.databind.serialization.SerializerFactory
import org.cirjson.cirjackson.databind.serialization.Serializers
import org.cirjson.cirjackson.databind.serialization.ValueSerializerModifier
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.type.TypeModifier
import kotlin.reflect.KClass

/**
 * Default implementation of [CirJacksonModule.SetupContext] used by [ObjectMapper].
 */
open class ModuleContextBase(protected val myBuilder: MapperBuilder<*, *>,
        protected val myConfigOverrides: ConfigOverrides) : CirJacksonModule.SetupContext {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Method called after all changes have been applied through this context, to propagate buffered or pending changes
     * (if any) back to builder. Note that base implementation does nothing here; it is only provided in case subclasses
     * might need it.
     */
    open fun applyChanges(builder: MapperBuilder<*, *>) {
        // No-op for base class
    }

    /*
     *******************************************************************************************************************
     * Accessors for metadata
     *******************************************************************************************************************
     */

    override val mapperVersion: Version
        get() = PackageVersion.VERSION

    override val formatName: String
        get() = streamFactory().formatName

    /*
     *******************************************************************************************************************
     * Accessors for subset of handlers
     *******************************************************************************************************************
     */

    override val owner: Any
        get() = myBuilder

    override fun typeFactory(): TypeFactory {
        return myBuilder.typeFactory()
    }

    override fun tokenStreamFactory(): TokenStreamFactory {
        return streamFactory()
    }

    /*
     *******************************************************************************************************************
     * Accessors for on/off features
     *******************************************************************************************************************
     */

    override fun isEnabled(feature: MapperFeature): Boolean {
        return myBuilder.isEnabled(feature)
    }

    override fun isEnabled(feature: DeserializationFeature): Boolean {
        return myBuilder.isEnabled(feature)
    }

    override fun isEnabled(feature: SerializationFeature): Boolean {
        return myBuilder.isEnabled(feature)
    }

    override fun isEnabled(feature: TokenStreamFactory.Feature): Boolean {
        return streamFactory().isEnabled(feature)
    }

    override fun isEnabled(feature: StreamReadFeature): Boolean {
        return myBuilder.isEnabled(feature)
    }

    override fun isEnabled(feature: StreamWriteFeature): Boolean {
        return myBuilder.isEnabled(feature)
    }

    /*
     *******************************************************************************************************************
     * Mutators for adding deserializers, related
     *******************************************************************************************************************
     */

    override fun addDeserializers(deserializers: Deserializers): CirJacksonModule.SetupContext {
        set(deserializerFactory().withAdditionalDeserializers(deserializers))
        return this
    }

    override fun addKeyDeserializers(deserializers: KeyDeserializers): CirJacksonModule.SetupContext {
        set(deserializerFactory().withAdditionalKeyDeserializers(deserializers))
        return this
    }

    override fun addDeserializerModifier(modifier: ValueDeserializerModifier): CirJacksonModule.SetupContext {
        set(deserializerFactory().withDeserializerModifier(modifier))
        return this
    }

    override fun addValueInstantiators(instantiators: ValueInstantiators): CirJacksonModule.SetupContext {
        set(deserializerFactory().withValueInstantiators(instantiators))
        return this
    }

    /*
     *******************************************************************************************************************
     * Mutators for adding serializers, related
     *******************************************************************************************************************
     */

    override fun addSerializers(serializers: Serializers): CirJacksonModule.SetupContext {
        set(serializerFactory().withAdditionalSerializers(serializers))
        return this
    }

    override fun addKeySerializers(serializers: Serializers): CirJacksonModule.SetupContext {
        set(serializerFactory().withAdditionalKeySerializers(serializers))
        return this
    }

    override fun addSerializerModifier(modifier: ValueSerializerModifier): CirJacksonModule.SetupContext {
        set(serializerFactory().withSerializerModifier(modifier))
        return this
    }

    override fun overrideDefaultNullKeySerializer(serializer: ValueSerializer<*>): CirJacksonModule.SetupContext {
        set(serializerFactory().withNullKeySerializers(serializer))
        return this
    }

    override fun overrideDefaultNullValueSerializer(serializer: ValueSerializer<*>): CirJacksonModule.SetupContext {
        set(serializerFactory().withNullValueSerializers(serializer))
        return this
    }

    /*
     *******************************************************************************************************************
     * Mutators for annotation introspection
     *******************************************************************************************************************
     */

    override fun insertAnnotationIntrospector(
            annotationIntrospector: AnnotationIntrospector): CirJacksonModule.SetupContext {
        myBuilder.baseSettings(myBuilder.baseSettings().withInsertedAnnotationIntrospector(annotationIntrospector))
        return this
    }

    override fun appendAnnotationIntrospector(
            annotationIntrospector: AnnotationIntrospector): CirJacksonModule.SetupContext {
        myBuilder.baseSettings(myBuilder.baseSettings().withAppendedAnnotationIntrospector(annotationIntrospector))
        return this
    }

    /*
     *******************************************************************************************************************
     * Mutators for type handling
     *******************************************************************************************************************
     */

    override fun addAbstractTypeResolver(resolver: AbstractTypeResolver): CirJacksonModule.SetupContext {
        myBuilder.addAbstractTypeResolver(resolver)
        return this
    }

    override fun addTypeModifier(modifier: TypeModifier): CirJacksonModule.SetupContext {
        myBuilder.addTypeModifier(modifier)
        return this
    }

    override fun registerSubtypes(vararg subtypes: KClass<*>): CirJacksonModule.SetupContext {
        myBuilder.subtypeResolver().registerSubtypes(*subtypes)
        return this
    }

    override fun registerSubtypes(vararg subtypes: NamedType): CirJacksonModule.SetupContext {
        myBuilder.subtypeResolver().registerSubtypes(*subtypes)
        return this
    }

    override fun registerSubtypes(subtypes: Collection<KClass<*>>): CirJacksonModule.SetupContext {
        myBuilder.subtypeResolver().registerSubtypes(subtypes)
        return this
    }

    /*
     *******************************************************************************************************************
     * Mutators, other
     *******************************************************************************************************************
     */

    override fun configOverride(type: KClass<*>): MutableConfigOverride {
        return myConfigOverrides.findOrCreateOverride(type)
    }

    override fun addHandler(handler: DeserializationProblemHandler): CirJacksonModule.SetupContext {
        myBuilder.addHandler(handler)
        return this
    }

    override fun overrideInjectableValues(
            values: (InjectableValues?) -> InjectableValues?): CirJacksonModule.SetupContext {
        val oldValues = myBuilder.injectableValues()
        val newValues = values(oldValues)

        if (newValues !== oldValues) {
            myBuilder.injectableValues(newValues)
        }

        return this
    }

    override fun setMixIn(target: KClass<*>, mixinSource: KClass<*>): CirJacksonModule.SetupContext {
        myBuilder.addMixIn(target, mixinSource)
        return this
    }

    /*
     *******************************************************************************************************************
     * Internal/subclass helper methods
     *******************************************************************************************************************
     */

    protected open fun streamFactory(): TokenStreamFactory {
        return myBuilder.streamFactory()
    }

    protected open fun deserializerFactory(): DeserializerFactory {
        return myBuilder.deserializerFactory()
    }

    protected open fun set(factory: DeserializerFactory?) {
        myBuilder.deserializerFactory(factory)
    }

    protected open fun serializerFactory(): SerializerFactory {
        return myBuilder.serializerFactory()
    }

    protected open fun set(factory: SerializerFactory?) {
        myBuilder.serializerFactory(factory)
    }

}