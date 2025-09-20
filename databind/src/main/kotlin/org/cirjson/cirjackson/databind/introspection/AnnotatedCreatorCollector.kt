package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import kotlin.reflect.KClass

class AnnotatedCreatorCollector private constructor(config: MapperConfig<*>?, private val myPrimaryType: KotlinType,
        private val myTypeContext: TypeResolutionContext, private val myCollectAnnotations: Boolean) :
        CollectorBase(config) {

    companion object {

        fun collectCreators(config: MapperConfig<*>?, context: TypeResolutionContext, type: KotlinType,
                primaryMixIn: KClass<*>?, collectAnnotations: Boolean): AnnotatedClass.Creators {
            TODO("Not yet implemented")
        }

    }

}