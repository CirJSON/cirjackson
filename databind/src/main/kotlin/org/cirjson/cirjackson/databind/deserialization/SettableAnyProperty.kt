package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.jdk.JDKValueInstantiators
import org.cirjson.cirjackson.databind.introspection.AnnotatedField
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.node.ObjectNode
import org.cirjson.cirjackson.databind.util.*
import kotlin.reflect.KClass

/**
 * Class that represents a "wildcard" set method which can be used to generically set values of otherwise unmapped (aka
 * "unknown") properties read from CirJSON content.
 *
 * @property myProperty Method used for setting "any" properties, along with annotation information. Retained to allow
 * contextualization of any properties.
 *
 * @property mySetter Annotated variant is needed for JDK serialization only
 */
abstract class SettableAnyProperty(protected val myProperty: BeanProperty, protected val mySetter: AnnotatedMember?,
        protected val myType: KotlinType, protected val myKeyDeserializer: KeyDeserializer?,
        protected val myValueDeserializer: ValueDeserializer<Any>?,
        protected val myValueTypeDeserializer: TypeDeserializer?) {

    protected val mySetterIsField = mySetter is AnnotatedField

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    abstract fun withValueDeserializer(deserializer: ValueDeserializer<Any>?): SettableAnyProperty

    open fun fixAccess(config: DeserializationConfig) {
        mySetter!!.fixAccess(config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
    }

    /*
     *******************************************************************************************************************
     * Public API, accessors
     *******************************************************************************************************************
     */

    open val property: BeanProperty
        get() = myProperty

    open fun hasValueDeserializer(): Boolean {
        return myValueDeserializer != null
    }

    open val type: KotlinType
        get() = myType

    open val propertyName: String
        get() = myProperty.name

    /**
     * Accessor for parameterIndex.
     * @return `-1` if not a parameterized setter, otherwise index of parameter
     */
    open val parameterIndex: Int
        get() = -1

    /**
     * Create an instance of value to pass through Creator parameter.
     */
    open fun createParameterObject(): Any {
        throw UnsupportedOperationException("Cannot call createParameterObject() on ${this::class.qualifiedName}")
    }

    /*
     *******************************************************************************************************************
     * Public API, deserialization
     *******************************************************************************************************************
     */

    /**
     * Method called to deserialize appropriate value, given parser (and context), and set it using appropriate method
     * (a setter method).
     */
    @Throws(CirJacksonException::class)
    open fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any,
            propertyName: String) {
        try {
            val key = if (myKeyDeserializer == null) {
                propertyName
            } else {
                myKeyDeserializer.deserializeKey(propertyName, context)
            }
        } catch (reference: UnresolvedForwardReferenceException) {
            if (myValueDeserializer!!.getObjectIdReader(context) == null) {
                throw DatabindException.from(parser, "Unresolved forward reference but no identity info.", reference)
            }

            val referring = AnySetterReferring(this, reference, myType.rawClass, instance, propertyName)
            reference.readableObjectId!!.appendReferring(referring)
        }
    }

    @Throws(CirJacksonException::class)
    open fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        val token = parser.currentToken()

        return if (token == CirJsonToken.VALUE_NULL) {
            myValueDeserializer!!.getNullValue(context)
        } else if (myValueTypeDeserializer != null) {
            myValueDeserializer!!.deserializeWithType(parser, context, myValueTypeDeserializer)
        } else {
            myValueDeserializer!!.deserialize(parser, context)
        }
    }

    @Throws(CirJacksonException::class)
    open fun set(instance: Any, propertyName: Any?, value: Any?) {
        try {
            setInstance(instance, propertyName, value)
        } catch (e: CirJacksonException) {
            throw e
        } catch (e: Exception) {
            throwAsIOException(e, propertyName, value)
        }
    }

    @Throws(Exception::class)
    protected abstract fun setInstance(instance: Any, propertyName: Any?, value: Any?)

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    /**
     * @param exception Exception to re-throw or wrap
     *
     * @param propertyName Name of property (from CirJSON input) to set
     *
     * @param value Value of the property
     */
    protected open fun throwAsIOException(exception: Exception, propertyName: Any?, value: Any?): Nothing {
        if (exception is IllegalArgumentException) {
            val actualType = value.className
            val message = StringBuilder("Problem deserializing \"any-property\" '").append(propertyName)
            message.append("' of class ").append(className).append(" (expected type: ").append(myType)
            message.append("; actual type: ").append(actualType).append(')')
            val originalMessage = exception.exceptionMessage()

            if (originalMessage != null) {
                message.append(", problem: ").append(originalMessage)
            } else {
                message.append(" (no error message provided)")
            }

            throw DatabindException.from(null as CirJsonParser?, message.toString(), exception)
        }

        val throwable = exception.throwIfCirJacksonException().throwIfRuntimeException().rootCause
        throw DatabindException.from(null as CirJsonParser?, throwable.exceptionMessage(), throwable)
    }

    private val className: String
        get() = mySetter!!.declaringClass.name

    override fun toString(): String {
        return "[any property on class $className]"
    }

    private class AnySetterReferring(private val myParent: SettableAnyProperty,
            reference: UnresolvedForwardReferenceException, beanType: KClass<*>, private val myPojo: Any,
            private val myPropertyName: String?) : ReadableObjectId.Referring(reference, beanType) {

        @Throws(CirJacksonException::class)
        override fun handleResolvedForwardReference(id: Any, value: Any?) {
            if (!hasId(id)) {
                throw IllegalArgumentException(
                        "Trying to resolve a forward reference with id [$id] that wasn't previously registered.")
            }

            myParent.set(myPojo, myPropertyName, value)
        }

    }

    /*
     *******************************************************************************************************************
     * Concrete implementations
     *******************************************************************************************************************
     */

    protected open class MethodAnyProperty(property: BeanProperty, setter: AnnotatedMember?, type: KotlinType,
            keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<Any>?,
            valueTypeDeserializer: TypeDeserializer?) :
            SettableAnyProperty(property, setter, type, keyDeserializer, valueDeserializer, valueTypeDeserializer) {

        override fun withValueDeserializer(deserializer: ValueDeserializer<Any>?): SettableAnyProperty {
            return MethodAnyProperty(myProperty, mySetter, myType, myKeyDeserializer, deserializer,
                    myValueTypeDeserializer)
        }

        @Throws(Exception::class)
        override fun setInstance(instance: Any, propertyName: Any?, value: Any?) {
            (mySetter as AnnotatedMethod).callOnWith(instance, propertyName, value)
        }

    }

    protected open class MapFieldAnyProperty(protected val myContext: DeserializationContext, property: BeanProperty,
            setter: AnnotatedMember, type: KotlinType, keyDeserializer: KeyDeserializer?,
            valueDeserializer: ValueDeserializer<Any>?, valueTypeDeserializer: TypeDeserializer?,
            protected val myValueInstantiator: ValueInstantiator?) :
            SettableAnyProperty(property, setter, type, keyDeserializer, valueDeserializer, valueTypeDeserializer) {

        override fun withValueDeserializer(deserializer: ValueDeserializer<Any>?): SettableAnyProperty {
            return MapFieldAnyProperty(myContext, myProperty, mySetter!!, myType, myKeyDeserializer, deserializer,
                    myValueTypeDeserializer, myValueInstantiator)
        }

        @Suppress("UNCHECKED_CAST")
        @Throws(Exception::class)
        override fun setInstance(instance: Any, propertyName: Any?, value: Any?) {
            val field = mySetter as AnnotatedField
            val value =
                    field.getValue(instance) as MutableMap<Any?, Any?>? ?: createAndSetMap(myContext, field, instance,
                            propertyName)
            value[propertyName] = value
        }

        @Throws(Exception::class)
        @Suppress("SameParameterValue", "UNCHECKED_CAST")
        protected open fun createAndSetMap(context: DeserializationContext, field: AnnotatedField, instance: Any,
                propertyName: Any?): MutableMap<Any?, Any?> {
            myValueInstantiator ?: throw DatabindException.from(context,
                    "Cannot create an instance of ${myType.rawClass} for use as \"any-setter\" '${myProperty.name}'")
            val map = myValueInstantiator.createUsingDefault(context)!! as MutableMap<Any?, Any?>
            field.setValue(instance, map)
            return map
        }

    }

    protected open class CirJsonNodeFieldAnyProperty(property: BeanProperty, setter: AnnotatedMember?, type: KotlinType,
            valueDeserializer: ValueDeserializer<Any>?, protected val myNodeFactory: CirJsonNodeFactory) :
            SettableAnyProperty(property, setter, type, null, valueDeserializer, null) {

        override fun withValueDeserializer(deserializer: ValueDeserializer<Any>?): SettableAnyProperty {
            return this
        }

        @Throws(CirJacksonException::class)
        override fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any,
                propertyName: String) {
            setProperty(instance, propertyName, deserialize(parser, context) as CirJsonNode)
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
            return myValueDeserializer!!.deserialize(parser, context)
        }

        @Throws(Exception::class)
        override fun setInstance(instance: Any, propertyName: Any?, value: Any?) {
            setProperty(instance, propertyName as String?, value as CirJsonNode?)
        }

        @Throws(CirJacksonException::class)
        protected open fun setProperty(instance: Any, propertyName: String?, value: CirJsonNode?) {
            val field = mySetter as AnnotatedField
            val fieldValue = field.getValue(instance)

            val objectNode = if (fieldValue == null) {
                myNodeFactory.objectNode().also { field.setValue(instance, it) }
            } else if (fieldValue !is ObjectNode) {
                throw DatabindException.from(null as DeserializationContext?,
                        "Value \"any-setter\" '${this.propertyName}' not `ObjectNode` but ${fieldValue::class.name}")
            } else {
                fieldValue
            }

            objectNode[propertyName!!] = value
        }

    }

    protected open class MapParameterAnyProperty(property: BeanProperty, setter: AnnotatedMember, type: KotlinType,
            keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<Any>?,
            valueTypeDeserializer: TypeDeserializer?, protected val myValueInstantiator: ValueInstantiator,
            protected val myParameterIndex: Int) :
            SettableAnyProperty(property, setter, type, keyDeserializer, valueDeserializer, valueTypeDeserializer) {

        override fun withValueDeserializer(deserializer: ValueDeserializer<Any>?): SettableAnyProperty {
            return MapParameterAnyProperty(myProperty, mySetter!!, myType, myKeyDeserializer, deserializer,
                    myValueTypeDeserializer, myValueInstantiator, myParameterIndex)
        }

        @Suppress("UNCHECKED_CAST")
        @Throws(Exception::class)
        override fun setInstance(instance: Any, propertyName: Any?, value: Any?) {
            (instance as MutableMap<Any?, Any?>)[propertyName] = value
        }

        override val parameterIndex: Int
            get() = myParameterIndex

        override fun createParameterObject(): Any {
            return HashMap<Any?, Any?>()
        }

    }

    protected open class CirJsonNodeParameterAnyProperty(property: BeanProperty, setter: AnnotatedMember?,
            type: KotlinType, valueDeserializer: ValueDeserializer<Any>?,
            protected val myNodeFactory: CirJsonNodeFactory, protected val myParameterIndex: Int) :
            SettableAnyProperty(property, setter, type, null, valueDeserializer, null) {

        override fun withValueDeserializer(deserializer: ValueDeserializer<Any>?): SettableAnyProperty {
            throw UnsupportedOperationException("Cannot call withValueDeserializer() on ${this::class.qualifiedName}")
        }

        @Throws(CirJacksonException::class)
        override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
            return myValueDeserializer!!.deserialize(parser, context)
        }

        @Throws(Exception::class)
        override fun setInstance(instance: Any, propertyName: Any?, value: Any?) {
            (instance as ObjectNode)[propertyName as String] = value as CirJsonNode?
        }

    }

    companion object {

        fun constructForMethod(property: BeanProperty, setter: AnnotatedMember?, type: KotlinType,
                keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<Any>?,
                valueTypeDeserializer: TypeDeserializer?): SettableAnyProperty {
            return MethodAnyProperty(property, setter, type, keyDeserializer, valueDeserializer, valueTypeDeserializer)
        }

        fun constructForMapField(context: DeserializationContext, property: BeanProperty, setter: AnnotatedMember,
                type: KotlinType, keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<Any>?,
                valueTypeDeserializer: TypeDeserializer?): SettableAnyProperty {
            val mapType = setter.rawType.takeUnless { it == Map::class } ?: LinkedHashMap::class
            val valueInstantiator = JDKValueInstantiators.findStandardValueInstantiator(mapType)
            return MapFieldAnyProperty(context, property, setter, type, keyDeserializer, valueDeserializer,
                    valueTypeDeserializer, valueInstantiator)
        }

        fun constructForCirJsonNodeField(context: DeserializationContext, property: BeanProperty,
                setter: AnnotatedMember?, type: KotlinType,
                valueDeserializer: ValueDeserializer<Any>?): SettableAnyProperty {
            return CirJsonNodeFieldAnyProperty(property, setter, type, valueDeserializer, context.nodeFactory)
        }

        fun constructForMapParameter(property: BeanProperty, setter: AnnotatedMember, type: KotlinType,
                keyDeserializer: KeyDeserializer?, valueDeserializer: ValueDeserializer<Any>?,
                valueTypeDeserializer: TypeDeserializer?, parameterIndex: Int): SettableAnyProperty {
            val mapType = setter.rawType.takeUnless { it == Map::class } ?: LinkedHashMap::class
            val valueInstantiator = JDKValueInstantiators.findStandardValueInstantiator(mapType)!!
            return MapParameterAnyProperty(property, setter, type, keyDeserializer, valueDeserializer,
                    valueTypeDeserializer, valueInstantiator, parameterIndex)
        }

        fun constructForCirJsonNodeParameter(context: DeserializationContext, property: BeanProperty,
                setter: AnnotatedMember?, type: KotlinType, valueDeserializer: ValueDeserializer<Any>?,
                parameterIndex: Int): SettableAnyProperty {
            return CirJsonNodeParameterAnyProperty(property, setter, type, valueDeserializer, context.nodeFactory,
                    parameterIndex)
        }

    }

}