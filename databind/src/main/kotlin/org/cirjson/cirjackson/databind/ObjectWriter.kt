package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.core.Versioned
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

/**
 * Builder object that can be used for per-serialization configuration of serialization parameters, such as CirJSON View
 * and root type to use. (and thus fully thread-safe with no external synchronization); new instances are constructed
 * for different configurations. Instances are initially constructed by [ObjectMapper] and can be reused in completely
 * thread-safe manner with no explicit synchronization
 */
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

    /**
     * As a minor optimization, we will make an effort to pre-fetch a serializer, or at least relevant `TypeSerializer`,
     * if given enough information.
     * 
     * @property myRootType Specified root serialization type to use; can be same as runtime type, but usually one of
     * its super types (parent class or interface it implements).
     * 
     * @property myValueSerializer We may pre-fetch serializer if [myRootType] is known, and if so, reuse it afterward.
     * This allows avoiding further serializer lookups and increases performance a bit on cases where readers are
     * reused.
     * 
     * @property myTypeSerializer When dealing with polymorphic types, we cannot pre-fetch serializer, but can pre-fetch
     * [TypeSerializer].
     */
    class Prefetch internal constructor(private val myRootType: KotlinType?,
            private val myValueSerializer: ValueSerializer<Any>?, private val myTypeSerializer: TypeSerializer?) {

        val valueSerializer: ValueSerializer<Any>?
            get() = TODO("Not yet implemented")

        val typeSerializer: TypeSerializer?
            get() = TODO("Not yet implemented")

    }

}