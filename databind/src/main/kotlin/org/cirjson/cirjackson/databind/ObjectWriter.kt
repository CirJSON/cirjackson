package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.core.Versioned

open class ObjectWriter : Versioned {

    /*
     *******************************************************************************************************************
     * Lifecycle, constructors
     *******************************************************************************************************************
     */

    override fun version(): Version {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Serialization methods, others
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    open fun writeValueAsString(value: Any?): String {
        TODO("Not yet implemented")
    }

}