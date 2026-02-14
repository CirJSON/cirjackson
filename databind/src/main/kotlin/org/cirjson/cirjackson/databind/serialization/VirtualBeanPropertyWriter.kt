package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.ValueSerializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.util.Annotations
import kotlin.reflect.KClass

/**
 * [BeanPropertyWriter] implementation used with [org.cirjson.cirjackson.databind.annotation.CirJsonAppend] to add
 * "virtual" properties in addition to regular ones.
 *
 * @see org.cirjson.cirjackson.databind.serialization.implementation.AttributePropertyWriter
 */
abstract class VirtualBeanPropertyWriter : BeanPropertyWriter {

    /**
     * Constructor used by most subtypes.
     */
    protected constructor(propertyDefinition: BeanPropertyDefinition, contextAnnotations: Annotations?,
            declaredType: KotlinType?) : this(propertyDefinition, contextAnnotations, declaredType, null, null, null,
            propertyDefinition.findInclusion(), null)

    /**
     * Constructor that may be used by subclasses for constructing a "blue-print" instance; one that will only become
     * (or create) actual usable instance when its [withConfig] method is called.
     */
    protected constructor() : super()

    /**
     * Pass-through constructor that may be used by subclasses that want full control over implementation.
     */
    protected constructor(propertyDefinition: BeanPropertyDefinition, contextAnnotations: Annotations?,
            declaredType: KotlinType?, serializer: ValueSerializer<*>?, typeSerializer: TypeSerializer?,
            serializerType: KotlinType?, inclusion: CirJsonInclude.Value?, includeInViews: Array<KClass<*>>?) : super(
            propertyDefinition, propertyDefinition.primaryMember, contextAnnotations, declaredType, serializer,
            typeSerializer, serializerType, suppressNulls(inclusion), suppressableValue(inclusion), includeInViews)

    protected constructor(base: VirtualBeanPropertyWriter) : super(base)

    protected constructor(base: VirtualBeanPropertyWriter, name: PropertyName) : super(base, name)

    /*
     *******************************************************************************************************************
     * Standard accessor overrides
     *******************************************************************************************************************
     */

    override val isVirtual: Boolean
        get() = true

    /*
     *******************************************************************************************************************
     * Abstract methods for subclasses to define
     *******************************************************************************************************************
     */

    /**
     * Method called to figure out the value to serialize. For simple subtypes (such as
     * [org.cirjson.cirjackson.databind.serialization.implementation.AttributePropertyWriter]) this may be one of few
     * methods to define, although more advanced implementations may choose to not even use this method (by overriding
     * [serializeAsProperty]) and define a bogus implementation.
     */
    protected abstract fun value(bean: Any, generator: CirJsonGenerator, provider: SerializerProvider): Any?

    /**
     * Contextualization method called on a newly constructed virtual bean property. Usually a new instance needs to be
     * created due to finality of some of the configuration members; otherwise while recommended, creating a new
     * instance is not strictly-speaking mandatory because calls are made in thread-safe manner, as part of
     * initialization before use.
     *
     * @param config Current configuration; guaranteed to be
     * [SerializationConfig][org.cirjson.cirjackson.databind.SerializationConfig] (just not typed since caller does not
     * have dependency to serialization-specific types)
     * 
     * @param declaringClass Class that contains this property writer
     * 
     * @param propertyDefinition Nominal property definition to use
     * 
     * @param type Declared type for the property
     */
    abstract fun withConfig(config: MapperConfig<*>, declaringClass: AnnotatedClass,
            propertyDefinition: BeanPropertyDefinition, type: KotlinType): VirtualBeanPropertyWriter

    /*
     *******************************************************************************************************************
     * PropertyWriter methods: serialization
     *******************************************************************************************************************
     */

    @Throws(Exception::class)
    override fun serializeAsProperty(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
        val v = value(value, generator, provider)

        if (v == null) {
            if (myNullSerializer != null) {
                generator.writeName(myName!!)
                myNullSerializer!!.serializeNullable(null, generator, provider)
            }

            return
        }

        val serializer = if (mySerializer != null) {
            mySerializer!!
        } else {
            val clazz = v::class
            val map = myDynamicSerializers!!
            map.serializerFor(clazz) ?: findAndAddDynamic(map, clazz, provider)
        }

        if (mySuppressableValue != null) {
            if (MARKER_FOR_EMPTY === mySuppressableValue) {
                if (serializer.isEmpty(provider, v)) {
                    return
                }
            } else if (mySuppressableValue == v) {
                return
            }
        }

        if (v === value) {
            if (handleSelfReference(value, generator, provider, serializer)) {
                return
            }
        }

        generator.writeName(myName!!)

        if (myTypeSerializer == null) {
            serializer.serialize(v, generator, provider)
        } else {
            serializer.serializeWithType(v, generator, provider, myTypeSerializer!!)
        }
    }

    @Throws(Exception::class)
    override fun serializeAsElement(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
        val v = value(value, generator, provider)

        if (v == null) {
            if (myNullSerializer != null) {
                myNullSerializer!!.serializeNullable(null, generator, provider)
            } else {
                generator.writeNull()
            }

            return
        }

        val serializer = if (mySerializer != null) {
            mySerializer!!
        } else {
            val clazz = v::class
            val map = myDynamicSerializers!!
            map.serializerFor(clazz) ?: findAndAddDynamic(map, clazz, provider)
        }

        if (mySuppressableValue != null) {
            if (MARKER_FOR_EMPTY === mySuppressableValue) {
                if (serializer.isEmpty(provider, v)) {
                    return
                }
            } else if (mySuppressableValue == v) {
                return
            }
        }

        if (v === value) {
            if (handleSelfReference(value, generator, provider, serializer)) {
                return
            }
        }

        if (myTypeSerializer == null) {
            serializer.serialize(v, generator, provider)
        } else {
            serializer.serializeWithType(v, generator, provider, myTypeSerializer!!)
        }
    }

    companion object {

        fun suppressNulls(inclusion: CirJsonInclude.Value?): Boolean {
            inclusion ?: return false

            val valueInclusion = inclusion.valueInclusion
            return valueInclusion != CirJsonInclude.Include.ALWAYS &&
                    valueInclusion != CirJsonInclude.Include.USE_DEFAULTS
        }

        fun suppressableValue(inclusion: CirJsonInclude.Value?): Any? {
            inclusion ?: return false

            val valueInclusion = inclusion.valueInclusion

            if (valueInclusion == CirJsonInclude.Include.ALWAYS || valueInclusion == CirJsonInclude.Include.NON_NULL ||
                    valueInclusion == CirJsonInclude.Include.USE_DEFAULTS) {
                return null
            }

            return MARKER_FOR_EMPTY
        }

    }

}