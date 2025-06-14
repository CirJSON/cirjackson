package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.type.TypeFactory
import kotlin.reflect.full.starProjectedType

/**
 * Standard implementation of [Converter] that supports explicit type access, instead of relying on type detection of
 * generic type parameters.
 */
abstract class StandardConverter<IN, OUT> : Converter<IN, OUT> {

    /*
     *******************************************************************************************************************
     * Partial Converter API implementation
     *******************************************************************************************************************
     */

    override fun getInputType(typeFactory: TypeFactory): KotlinType {
        return findConverterType(typeFactory).containedType(0)!!
    }

    override fun getOutputType(typeFactory: TypeFactory): KotlinType {
        return findConverterType(typeFactory).containedType(1)!!
    }

    protected open fun findConverterType(typeFactory: TypeFactory): KotlinType {
        val thisType = typeFactory.constructType(this::class.starProjectedType)
        val converterType = thisType.findSuperType(Converter::class)

        if (converterType == null || converterType.containedTypeCount() < 2) {
            throw IllegalStateException(
                    "Cannot find OUT type parameter for Converter of type ${this::class.qualifiedName}")
        }

        return converterType
    }

}