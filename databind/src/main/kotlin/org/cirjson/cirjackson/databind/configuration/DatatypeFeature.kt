package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.util.CirJacksonFeature

/**
 * Interface that defines interaction with datatype-specific configuration features.
 */
interface DatatypeFeature : CirJacksonFeature {

    /**
     * Internal index used for efficient storage and index; no user-serviceable contents inside!
     */
    fun featureIndex(): Int

}