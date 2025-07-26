package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.databind.CirJacksonModule

open class ModuleContextBase(protected val myBuilder: MapperBuilder<*, *>,
        protected val myConfigOverrides: ConfigOverrides) : CirJacksonModule.SetupContext {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    open fun applyChanges(builder: MapperBuilder<*, *>) {
        TODO("Not yet implemented")
    }

}