package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.PropertyMetadata
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.introspection.ConcreteBeanPropertyBase
import kotlin.reflect.KClass

/**
 * Base class for writers used to output property values (name-value pairs) as key/value pairs via streaming API. This
 * is the most generic abstraction implemented by both POJO and [Map] serializers, and invoked by filtering
 * functionality.
 */
abstract class PropertyWriter : ConcreteBeanPropertyBase {

    protected constructor(metadata: PropertyMetadata?) : super(metadata)

    protected constructor(propertyDefinition: BeanPropertyDefinition) : super(propertyDefinition.metadata)

    constructor(base: PropertyWriter) : super(base)

    /*
     *******************************************************************************************************************
     * Metadata access
     *******************************************************************************************************************
     */

    /**
     * Convenience method for accessing annotation that may be associated either directly on property, or, if not, via
     * enclosing class (context). This allows adding baseline contextual annotations, for example, by adding an
     * annotation for a given class and making that apply to all properties unless overridden by per-property
     * annotations.
     * 
     * This method is functionally equivalent to:
     * ```
     * val ann = propWriter.getAnnotation(MyAnnotation::class) ?: propWriter.getContextAnnotation(MyAnnotation::class)
     * ```
     * that is, tries to find a property annotation first, but if one is not found, tries to find context-annotation
     * (from enclosing class) of same type.
     */
    open fun <A : Annotation> findAnnotation(annotationClass: KClass<A>): A? {
        return getAnnotation(annotationClass) ?: getContextAnnotation(annotationClass)
    }

    /**
     * Method for accessing annotations directly declared for property that this writer is associated with.
     */
    abstract override fun <A : Annotation> getAnnotation(clazz: KClass<A>): A?

    /**
     * Method for accessing annotations declared in context of the property that this writer is associated with; usually
     * this means annotations on enclosing class for property.
     */
    abstract override fun <A : Annotation> getContextAnnotation(clazz: KClass<A>): A?

    /*
     *******************************************************************************************************************
     * Serialization methods, regular output
     *******************************************************************************************************************
     */

    /**
     * The main serialization method called by filter when property is to be written as an Any property.
     */
    @Throws(Exception::class)
    abstract fun serializeAsProperty(value: Any, generator: CirJsonGenerator, provider: SerializerProvider)

    /**
     * Serialization method that filter needs to call in cases where a property value (key, value) is to be filtered,
     * but the underlying data format requires a placeholder of some kind. This is usually the case for tabular
     * (positional) data formats such as CSV.
     */
    @Throws(Exception::class)
    abstract fun serializeAsOmittedProperty(value: Any, generator: CirJsonGenerator, provider: SerializerProvider)

    /*
     *******************************************************************************************************************
     * Serialization methods, explicit positional/tabular formats
     *******************************************************************************************************************
     */

    /**
     * Serialization method called when output is to be done as an array, that is, not using property names. This is
     * needed when serializing container ([Collection], array) types, or POJOs using `tabular` ("as array") output
     * format.
     * 
     * Note that this mode of operation is independent of underlying data format; so it is typically NOT called for
     * fully tabular formats such as CSV, where logical output is still as form of POJOs.
     */
    @Throws(Exception::class)
    abstract fun serializeAsElement(value: Any, generator: CirJsonGenerator, provider: SerializerProvider)

    /**
     * Serialization method called when doing tabular (positional) output from databind, but then value is to be
     * omitted. This requires the output of a placeholder value of some sort; often similar to
     * [serializeAsOmittedProperty].
     */
    @Throws(Exception::class)
    abstract fun serializeAsOmittedElement(value: Any, generator: CirJsonGenerator, provider: SerializerProvider)

    /*
     *******************************************************************************************************************
     * Schema-related
     *******************************************************************************************************************
     */

    /**
     * Traversal method used for things like CirJSON Schema generation, or POJO introspection.
     */
    abstract override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider)

}