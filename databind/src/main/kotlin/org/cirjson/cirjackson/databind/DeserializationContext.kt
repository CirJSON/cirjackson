package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.tree.ArrayTreeNode
import org.cirjson.cirjackson.core.tree.ObjectTreeNode
import org.cirjson.cirjackson.core.type.ResolvedType
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import org.cirjson.cirjackson.databind.deserialization.DeserializerCache
import org.cirjson.cirjackson.databind.deserialization.DeserializerFactory
import kotlin.reflect.KClass

abstract class DeserializationContext protected constructor(protected val myStreamFactory: TokenStreamFactory,
        protected val myFactory: DeserializerFactory, protected val myCache: DeserializerCache,
        protected val myConfig: DeserializationConfig, protected val mySchema: FormatSchema?,
        protected val myInjectableValues: InjectableValues?) : DatabindContext(), ObjectReadContext {

    /*
     *******************************************************************************************************************
     * DatabindContext implementation
     *******************************************************************************************************************
     */

    override val config: DeserializationConfig
        get() = TODO("Not yet implemented")

    override val annotationIntrospector: AnnotationIntrospector
        get() = TODO("Not yet implemented")

    override fun constructSpecializedType(baseType: KotlinType, subclass: KClass<*>): KotlinType {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * ObjectReadContext implementation, config access
     *******************************************************************************************************************
     */

    override val tokenStreamFactory: TokenStreamFactory
        get() = TODO("Not yet implemented")

    override val schema: FormatSchema?
        get() = TODO("Not yet implemented")

    override val streamReadConstraints: StreamReadConstraints
        get() = TODO("Not yet implemented")

    override fun getStreamReadFeatures(defaults: Int): Int {
        TODO("Not yet implemented")
    }

    override fun getFormatReadFeatures(defaults: Int): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * ObjectReadContext implementation, Tree creation
     *******************************************************************************************************************
     */

    override fun createArrayNode(): ArrayTreeNode {
        TODO("Not yet implemented")
    }

    override fun createObjectNode(): ObjectTreeNode {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * ObjectReadContext implementation, databind
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun <T : TreeNode> readTree(parser: CirJsonParser): T {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun <T> readValue(parser: CirJsonParser, clazz: Class<T>): T {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun <T> readValue(parser: CirJsonParser, typeReference: TypeReference<T>): T {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun <T> readValue(parser: CirJsonParser, resolvedType: ResolvedType): T {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, config feature accessors
     *******************************************************************************************************************
     */

    fun isEnabled(feature: DeserializationFeature): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, accessor for helper objects
     *******************************************************************************************************************
     */

    val parser: CirJsonParser?
        get() = TODO("Not implemented")

    /*
     *******************************************************************************************************************
     * Miscellaneous config access
     *******************************************************************************************************************
     */

    open fun hasExplicitDeserializerFor(valueType: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API, value deserializer access
     *******************************************************************************************************************
     */

    fun findContextualValueDeserializer(type: KotlinType, property: BeanProperty?): ValueDeserializer<Any>? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Methods for problem handling
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    open fun handleUnknownTypeId(baseType: KotlinType, id: String, idResolver: TypeIdResolver,
            extraDescription: String): KotlinType? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    open fun handleMissingTypeId(baseType: KotlinType, idResolver: TypeIdResolver,
            extraDescription: String): KotlinType? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Methods for problem reporting, in cases where recovery is not considered possible: input problem
     *******************************************************************************************************************
     */

    @Throws(DatabindException::class)
    open fun <T> reportWrongTokenException(targetType: KClass<*>, expectedToken: CirJsonToken, message: String): T {
        TODO("Not yet implemented")
    }

    @Throws(DatabindException::class)
    open fun <T> reportInputMismatch(targetType: KotlinType?, message: String): T {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Methods for problem reporting, in cases where recovery is not considered possible: POJO definition problem
     *******************************************************************************************************************
     */

    @Throws(DatabindException::class)
    override fun <T> reportBadDefinition(type: KotlinType, message: String): T {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Methods for constructing semantic exceptions; usually not to be called directly, call `handleXxx()` instead
     *******************************************************************************************************************
     */

    override fun invalidTypeIdException(baseType: KotlinType, typeId: String,
            extraDescription: String): DatabindException {
        TODO("Not yet implemented")
    }

}