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
import kotlin.reflect.KClass

open class SerializationContextExtension protected constructor(streamFactory: TokenStreamFactory,
        config: SerializationConfig, generatorConfig: GeneratorSettings, factory: SerializerFactory,
        serializerCache: SerializerCache) :
        SerializerProvider(streamFactory, config, generatorConfig, factory, serializerCache) {

    /*
     *******************************************************************************************************************
     * Abstract method implementations, factory methods
     *******************************************************************************************************************
     */

    override fun serializerInstance(annotated: Annotated, serializerDefinition: Any?): ValueSerializer<Any>? {
        TODO("Not yet implemented")
    }

    override fun includeFilterInstance(forProperty: BeanProperty?, filterClass: KClass<*>?): Any? {
        TODO("Not yet implemented")
    }

    override fun includeFilterSuppressNulls(filter: Any?): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Abstract method implementations, serialization-like methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun <T : CirJsonNode> valueToTree(fromValue: Any?): T {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Object Id handling
     *******************************************************************************************************************
     */

    override fun findObjectId(forPojo: Any, generatorType: ObjectIdGenerator<*>): WritableObjectId {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Extended API called by ObjectMapper: value serialization
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    open fun serializeValue(generator: CirJsonGenerator, value: Any?) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    open fun serializeValue(generator: CirJsonGenerator, value: Any?, rootType: KotlinType?) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    open fun serializeValue(generator: CirJsonGenerator, value: Any?, rootType: KotlinType?,
            serializer: ValueSerializer<Any>?) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    open fun serializePolymorphic(generator: CirJsonGenerator, value: Any?, rootType: KotlinType?,
            serializer: ValueSerializer<Any>?, typeSerializer: TypeSerializer) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Extended API called by ObjectMapper: other
     *******************************************************************************************************************
     */

    open fun acceptCirJsonFormatVisitor(type: KotlinType, visitor: CirJsonFormatVisitorWrapper) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    class Implementation(streamFactory: TokenStreamFactory, config: SerializationConfig,
            generatorConfig: GeneratorSettings, factory: SerializerFactory, serializerCache: SerializerCache) :
            SerializationContextExtension(streamFactory, config, generatorConfig, factory, serializerCache)

}