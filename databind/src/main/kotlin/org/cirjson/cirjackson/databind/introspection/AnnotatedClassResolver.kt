package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.configuration.MapperConfig
import kotlin.reflect.KClass

open class AnnotatedClassResolver {

    companion object {

        /*
         ***************************************************************************************************************
         * Public static API
         ***************************************************************************************************************
         */

        fun resolveWithoutSuperTypes(config: MapperConfig<*>, forType: KClass<*>): AnnotatedClass {
            TODO("Not yet implemented")
        }

    }

}