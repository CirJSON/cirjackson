package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor
import org.cirjson.cirjackson.databind.introspection.ConcreteBeanPropertyBase
import kotlin.reflect.KClass

abstract class SettableBeanProperty : ConcreteBeanPropertyBase {

    /*
     *******************************************************************************************************************
     * Configuration that is not yet immutable; generally assigned during initialization process but cannot be passed to
     *  constructor.
     *******************************************************************************************************************
     */

    protected var myPropertyIndex = -1

    /*
     *******************************************************************************************************************
     * Lifecycle (construct & configure)
     *******************************************************************************************************************
     */

    protected constructor(source: SettableBeanProperty) : super(source) {
    }

    abstract fun withValueDeserializer(deserializer: ValueDeserializer<*>?): SettableBeanProperty

    open val isIgnorable: Boolean
        get() = false

    /*
     *******************************************************************************************************************
     * BeanProperty implementation
     *******************************************************************************************************************
     */

    override val name: String
        get() = TODO("Not yet implemented")

    override val fullName: PropertyName
        get() = TODO("Not yet implemented")

    override val type: KotlinType
        get() = TODO("Not yet implemented")

    override val wrapperName: PropertyName?
        get() = TODO("Not yet implemented")

    override fun <A : Annotation> getContextAnnotation(clazz: KClass<A>): A? {
        TODO("Not yet implemented")
    }

    override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open fun hasValueDeserializer(): Boolean {
        TODO("Not yet implemented")
    }

    open val valueDeserializer: ValueDeserializer<Any>?
        get() {
            TODO("Not yet implemented")
        }

    open val propertyIndex: Int
        get() = myPropertyIndex

    open val injectableValueId: Any?
        get() = null

    open val isInjectionOnly: Boolean
        get() = false

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

}