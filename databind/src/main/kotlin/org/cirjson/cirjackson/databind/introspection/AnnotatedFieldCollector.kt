package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import kotlin.reflect.KClass

class AnnotatedFieldCollector private constructor(config: MapperConfig<*>?, private val myMixInResolver: MixInResolver?,
        private val myCollectAnnotations: Boolean) : CollectorBase(config) {

    companion object {

        fun collectFields(config: MapperConfig<*>?, context: TypeResolutionContext, mixins: MixInResolver?,
                type: KotlinType, primaryMixIn: KClass<*>?, collectAnnotations: Boolean): List<AnnotatedField> {
            TODO("Not yet implemented")
        }

    }

}