package org.cirjson.cirjackson.databind

abstract class CirJacksonModule {

    /*
     *******************************************************************************************************************
     * Lifecycle: registration
     *******************************************************************************************************************
     */

    abstract fun setupModule(context: SetupContext)

    interface SetupContext {
    }

}