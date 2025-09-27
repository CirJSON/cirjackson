package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.type.TypeBindings
import org.cirjson.cirjackson.databind.util.isJdkClass
import kotlin.reflect.KClass

open class AnnotatedClassResolver {

    private val myConfig: MapperConfig<*>

    private val myIntrospector: AnnotationIntrospector?

    private val myMixInResolver: MixInResolver?

    private val myBindings: TypeBindings

    private val myType: KotlinType?

    private val myClass: KClass<*>

    private val myPrimaryMixin: KClass<*>?

    private val myCollectAnnotations: Boolean

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    internal constructor(config: MapperConfig<*>, type: KotlinType, resolver: MixInResolver?) {
        myConfig = config
        myType = type
        myClass = type.rawClass
        myMixInResolver = resolver
        myBindings = type.bindings
        myIntrospector = config.takeIf { it.isAnnotationProcessingEnabled }?.annotationIntrospector
        myPrimaryMixin = resolver?.findMixInClassFor(myClass)

        myCollectAnnotations = myIntrospector != null && (myClass.isJdkClass || !myType.isContainerType)
    }

    internal constructor(config: MapperConfig<*>, clazz: KClass<*>, resolver: MixInResolver?) {
        myConfig = config
        myType = null
        myClass = clazz
        myMixInResolver = resolver
        myBindings = TypeBindings.EMPTY
        myIntrospector = config.takeIf { it.isAnnotationProcessingEnabled }?.annotationIntrospector
        myPrimaryMixin = resolver?.findMixInClassFor(myClass)

        myCollectAnnotations = myIntrospector != null
    }

    companion object {

        private val NO_ANNOTATIONS = emptyArray<Annotation>()

        private val EMPTY_ANNOTATIONS = AnnotationCollector.EMPTY_ANNOTATIONS

        private val CLASS_OBJECT = Any::class

        private val CLASS_ENUM = Enum::class

        private val CLASS_LIST = List::class

        private val CLASS_MAP = Map::class

        /*
         ***************************************************************************************************************
         * Public static API
         ***************************************************************************************************************
         */

        fun resolveWithoutSuperTypes(config: MapperConfig<*>?, forType: KClass<*>): AnnotatedClass {
            TODO("Not yet implemented")
        }

    }

}