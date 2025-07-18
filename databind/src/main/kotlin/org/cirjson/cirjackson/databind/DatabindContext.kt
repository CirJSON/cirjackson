package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import kotlin.reflect.KClass

abstract class DatabindContext {

    /*
     *******************************************************************************************************************
     * Generic config access
     *******************************************************************************************************************
     */

    abstract val config: MapperConfig<*>

    abstract val annotationIntrospector: AnnotationIntrospector

    /*
     *******************************************************************************************************************
     *  Annotation, BeanDescription introspection
     *******************************************************************************************************************
     */

    fun introspectClassAnnotations(rawType: KClass<*>): AnnotatedClass {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Error reporting
     *******************************************************************************************************************
     */

    @Throws(DatabindException::class)
    abstract fun <T> reportBadDefinition(type: KotlinType, message: String): T

}