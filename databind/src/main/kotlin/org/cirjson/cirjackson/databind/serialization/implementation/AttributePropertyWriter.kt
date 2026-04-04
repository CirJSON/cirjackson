package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.serialization.VirtualBeanPropertyWriter
import org.cirjson.cirjackson.databind.util.Annotations

/**
 * [VirtualBeanPropertyWriter] implementation used for [org.cirjson.cirjackson.databind.annotation.CirJsonAppend], to
 * serialize properties backed-by dynamically assignable attribute values.
 */
open class AttributePropertyWriter : VirtualBeanPropertyWriter {

    protected val myAttributeName: String

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(attributeName: String, propertyDefinition: BeanPropertyDefinition,
            contextAnnotations: Annotations?, declaredType: KotlinType?) : this(attributeName, propertyDefinition,
            contextAnnotations, declaredType, propertyDefinition.findInclusion())

    protected constructor(attributeName: String, propertyDefinition: BeanPropertyDefinition,
            contextAnnotations: Annotations?, declaredType: KotlinType?, inclusion: CirJsonInclude.Value?) : super(
            propertyDefinition, contextAnnotations, declaredType, null, null, null, inclusion, null) {
        myAttributeName = attributeName
    }

    protected constructor(base: AttributePropertyWriter) : super(base) {
        myAttributeName = base.myAttributeName
    }

    /**
     * Since this method should typically not be called on this subtype, default implementation simply throws an [IllegalStateException].
     */
    override fun withConfig(config: MapperConfig<*>, declaringClass: AnnotatedClass,
            propertyDefinition: BeanPropertyDefinition, type: KotlinType): VirtualBeanPropertyWriter {
        throw IllegalStateException("Should not be called on this type")
    }

    /*
     *******************************************************************************************************************
     * Overrides for actual serialization, value access
     *******************************************************************************************************************
     */

    override fun value(bean: Any, generator: CirJsonGenerator, provider: SerializerProvider): Any? {
        return provider.getAttribute(bean)
    }

    companion object {

        fun construct(attributeName: String, propertyDefinition: BeanPropertyDefinition,
                contextAnnotations: Annotations?, declaredType: KotlinType?): AttributePropertyWriter {
            return AttributePropertyWriter(attributeName, propertyDefinition, contextAnnotations, declaredType)
        }

    }

}