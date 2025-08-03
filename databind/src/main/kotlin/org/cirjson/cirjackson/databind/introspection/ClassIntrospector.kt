package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.configuration.MapperConfig

abstract class ClassIntrospector protected constructor() {

    abstract fun forOperation(config: MapperConfig<*>): ClassIntrospector

}