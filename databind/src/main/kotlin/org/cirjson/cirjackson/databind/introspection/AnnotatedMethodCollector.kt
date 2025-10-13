package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import kotlin.reflect.KClass

class AnnotatedMethodCollector private constructor(config: MapperConfig<*>?, mixInResolver: MixInResolver?,
        private val myCollectAnnotations: Boolean) : CollectorBase(config) {

    companion object {

        fun collectMethods(config: MapperConfig<*>?, context: TypeResolutionContext, mixins: MixInResolver?,
                type: KotlinType, superTypes: List<KotlinType>, primaryMixIn: KClass<*>?,
                collectAnnotations: Boolean): AnnotatedMethodMap {
            TODO("Not yet implemented")
        }

    }

}