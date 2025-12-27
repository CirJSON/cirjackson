package org.cirjson.cirjackson.databind.node

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.ObjectReadContext
import org.cirjson.cirjackson.databind.CirJsonNode

/**
 * Abstract base class common to all standard [CirJsonNode] implementations. The main addition here is that we declare
 * that subclasses must implement [CirJacksonSerializable]. This simplifies object mapping aspects a bit, as no external
 * serializers are needed.
 * 
 * Note that support for [java.io.Serializable] is added here and so all subtypes are fully JDK serializable: but also
 * note that serialization is as CirJSON and should only be used for interoperability purposes where other approaches
 * are not available.
 */
abstract class BaseCirJsonNode protected constructor() : CirJsonNode() {

    /*
     *******************************************************************************************************************
     * Defaulting for introspection
     *******************************************************************************************************************
     */

    override val isEmbeddedValue: Boolean
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Basic definitions for non-container types
     *******************************************************************************************************************
     */

    /*
     *******************************************************************************************************************
     * Support for traversal-as-stream
     *******************************************************************************************************************
     */

    override fun traverse(objectReadContext: ObjectReadContext): CirJsonParser {
        TODO("Not yet implemented")
    }

    override val numberType: CirJsonParser.NumberType?
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * withXxx traversal
     *******************************************************************************************************************
     */

}