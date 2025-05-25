package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.MapperFeature
import org.cirjson.cirjackson.databind.introspection.MixInResolver
import kotlin.reflect.KClass

abstract class MapperConfig<T : MapperConfig<T>> : MixInResolver {

    /*
     *******************************************************************************************************************
     * Configuration: simple features
     *******************************************************************************************************************
     */

    fun isEnabled(feature: MapperFeature): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Configuration: introspectors, mix-ins
     *******************************************************************************************************************
     */

    val annotationIntrospector: AnnotationIntrospector?
        get() {
            TODO("Not yet implemented")
        }

    /*
     *******************************************************************************************************************
     * Configuration: default settings with per-type overrides
     *******************************************************************************************************************
     */

    abstract fun getDefaultInclusion(baseType: KClass<*>, propertyType: KClass<*>): CirJsonInclude.Value

    abstract fun getDefaultPropertyFormat(baseType: KClass<*>): CirJsonFormat.Value

}