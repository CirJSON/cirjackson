package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.type.TypeFactory

/**
 * Helper interface for things that convert Objects of one type to another.
 *
 * NOTE: implementors are strongly encouraged to extend [StandardConverter] instead of directly implementing
 * [Converter], since that can help with the default implementation of typically boilerplate code.
 *
 * @param IN Type of values converter takes
 *
 * @param OUT Result type from conversion
 *
 * @see org.cirjson.cirjackson.databind.serialization.standard.StandardDelegatingSerializer
 *
 * @see org.cirjson.cirjackson.databind.deserialization.standard.StandardConvertingDeserializer
 */
interface Converter<IN, OUT> {

    /**
     * Main conversion method.
     */
    fun convert(input: IN): OUT

    /**
     * Method that can be used to find out actual input (source) type; this usually can be determined from type
     * parameters, but may need to be implemented differently from programmatically defined converters (which cannot
     * change static type parameter bindings).
     */
    fun getInputType(typeFactory: TypeFactory): KotlinType

    /**
     * Method that can be used to find out actual output (target) type; this usually can be determined from type
     * parameters, but may need to be implemented differently from programmatically defined converters (which cannot
     * change static type parameter bindings).
     */
    fun getOutputType(typeFactory: TypeFactory): KotlinType

    /**
     * This marker class is only to be used with annotations, to indicate that **no converter is to be used**.
     *
     * Specifically, this class is to be used as the marker for annotation
     * [org.cirjson.cirjackson.databind.annotation.CirJsonSerialize], property `converter` (and related)
     */
    abstract class None private constructor() : Converter<Any, Any>

}