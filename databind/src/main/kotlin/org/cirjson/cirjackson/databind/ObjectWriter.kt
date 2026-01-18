package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.core.Versioned
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

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

    /*
     *******************************************************************************************************************
     * Helper classes for configuration
     *******************************************************************************************************************
     */

    class Prefetch internal constructor(private val myRootType: KotlinType?,
            private val myValueSerializer: ValueSerializer<Any>?, private val myTypeSerializer: TypeSerializer?) {

        val valueSerializer: ValueSerializer<Any>?
            get() = TODO("Not yet implemented")

        val typeSerializer: TypeSerializer?
            get() = TODO("Not yet implemented")

    }

}