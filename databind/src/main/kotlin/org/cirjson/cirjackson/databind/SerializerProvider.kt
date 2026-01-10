package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.io.CharacterEscapes
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.databind.configuration.GeneratorSettings
import org.cirjson.cirjackson.databind.serialization.SerializerCache
import org.cirjson.cirjackson.databind.serialization.SerializerFactory
import org.cirjson.cirjackson.databind.type.TypeFactory
import kotlin.reflect.KClass

abstract class SerializerProvider : DatabindContext, ObjectWriteContext {

    /*
     *******************************************************************************************************************
     * Configuration, general
     *******************************************************************************************************************
     */

    private val myConfig: SerializationConfig

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(streamFactory: TokenStreamFactory, config: SerializationConfig,
            generatorConfig: GeneratorSettings, factory: SerializerFactory,
            serializerCache: SerializerCache) : super() {
        this.myConfig = config
    }

    protected constructor(source: SerializerProvider, serializerCache: SerializerCache) : super() {
        myConfig = source.myConfig
    }

    /*
     *******************************************************************************************************************
     * ObjectWriteContext implementation, config access
     *******************************************************************************************************************
     */

    override val tokenStreamFactory: TokenStreamFactory
        get() = TODO("Not yet implemented")

    override val schema: FormatSchema?
        get() = TODO("Not yet implemented")

    override val characterEscapes: CharacterEscapes?
        get() = TODO("Not yet implemented")

    override val prettyPrinter: PrettyPrinter?
        get() = TODO("Not yet implemented")

    override val isPrettyPrinterNotNull: Boolean
        get() = TODO("Not yet implemented")

    override fun getRootValueSeparator(defaultSeparator: SerializableString?): SerializableString? {
        TODO("Not yet implemented")
    }

    override fun getStreamWriteFeatures(defaults: Int): Int {
        TODO("Not yet implemented")
    }

    override fun getFormatWriteFeatures(defaults: Int): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * ObjectWriteContext implementation, databind integration
     *******************************************************************************************************************
     */

    override fun createArrayNode(): ArrayTreeNode {
        TODO("Not yet implemented")
    }

    override fun createObjectNode(): ObjectTreeNode {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeValue(generator: CirJsonGenerator, value: Any?) {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun writeTree(generator: CirJsonGenerator, value: TreeNode) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * DatabindContext implementation (and closely related but serialization-specific)
     *******************************************************************************************************************
     */

    final override val config: SerializationConfig
        get() = TODO("Not yet implemented")

    final override val annotationIntrospector: AnnotationIntrospector?
        get() = TODO("Not yet implemented")

    override val typeFactory: TypeFactory
        get() = TODO("Not yet implemented")

    override fun constructSpecializedType(baseType: KotlinType, subclass: KClass<*>): KotlinType {
        TODO("Not yet implemented")
    }

    override fun isEnabled(feature: MapperFeature): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Annotation, BeanDescription introspection
     *******************************************************************************************************************
     */

    override fun introspectBeanDescription(type: KotlinType): BeanDescription {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Access to other on/off features
     *******************************************************************************************************************
     */

    fun isEnabled(feature: SerializationFeature): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Access to other helper objects
     *******************************************************************************************************************
     */

    open val generator: CirJsonGenerator
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Serializer discovery: root/non-property value serializers
     *******************************************************************************************************************
     */

    fun findTypedValueSerializer(rawType: KClass<*>, cache: Boolean): ValueSerializer<Any> {
        TODO("Not yet implemented")
    }

    fun findTypedValueSerializer(valueType: KotlinType, cache: Boolean): ValueSerializer<Any> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Convenience methods for serializing using default methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    fun defaultSerializeNullValue(generator: CirJsonGenerator) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Error reporting
     *******************************************************************************************************************
     */

    @Throws(DatabindException::class)
    override fun <T> reportBadTypeDefinition(bean: BeanDescription, message: String?): T {
        TODO("Not yet implemented")
    }

    @Throws(DatabindException::class)
    override fun <T> reportBadDefinition(type: KotlinType, message: String?): T {
        TODO("Not yet implemented")
    }

    override fun invalidTypeIdException(baseType: KotlinType, typeId: String?,
            extraDescription: String): DatabindException {
        TODO("Not yet implemented")
    }

}