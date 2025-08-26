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
     * Access to specific config settings
     *******************************************************************************************************************
     */

    abstract fun isEnabled(feature: MapperFeature): Boolean

    /*
     *******************************************************************************************************************
     * Type instantiation/resolution
     *******************************************************************************************************************
     */

    abstract fun constructSpecializedType(baseType: KotlinType, subclass: KClass<*>): KotlinType

    abstract fun invalidTypeIdException(baseType: KotlinType, typeId: String,
            extraDescription: String): DatabindException

    /*
     *******************************************************************************************************************
     *  Annotation, BeanDescription introspection
     *******************************************************************************************************************
     */

    abstract fun introspectBeanDescription(type: KotlinType): BeanDescription

    open fun introspectClassAnnotations(rawType: KotlinType): AnnotatedClass {
        TODO("Not yet implemented")
    }

    open fun introspectClassAnnotations(rawType: KClass<*>): AnnotatedClass {
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