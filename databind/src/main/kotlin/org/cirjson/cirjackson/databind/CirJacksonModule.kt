package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.Versioned

abstract class CirJacksonModule : Versioned {

    /*
     *******************************************************************************************************************
     * Simple accessors
     *******************************************************************************************************************
     */

    open val registrationId: Any
        get() = TODO("Not yet implemented")

    open val dependencies: Iterable<CirJacksonModule>
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Lifecycle: registration
     *******************************************************************************************************************
     */

    abstract fun setupModule(context: SetupContext)

    interface SetupContext {
    }

}