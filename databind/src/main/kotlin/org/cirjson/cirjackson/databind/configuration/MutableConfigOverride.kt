package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.*

/**
 * Extension of [ConfigOverride] that allows changing of contained configuration settings. Exposed to
 * [CirJacksonModules][org.cirjson.cirjackson.databind.CirJacksonModule] that want to set overrides, but not exposed to
 * functionality that wants to apply overrides.
 */
open class MutableConfigOverride : ConfigOverride {

    constructor() : super()

    constructor(source: MutableConfigOverride) : super(source)

    fun copy(): MutableConfigOverride {
        return MutableConfigOverride(this)
    }

    open fun setFormat(value: CirJsonFormat.Value?): MutableConfigOverride {
        format = value
        return this
    }

    /**
     * Override inclusion setting for all properties contained in POJOs of the associated type.
     *
     * @param value Inclusion setting to apply contained properties.
     */
    open fun setInclude(value: CirJsonInclude.Value?): MutableConfigOverride {
        include = value
        return this
    }

    /**
     * Override inclusion setting for properties of the associated type regardless of the type of the POJO containing
     * it.
     *
     * @param value Inclusion setting to apply for properties of the associated type.
     */
    open fun setIncludeAsProperty(value: CirJsonInclude.Value?): MutableConfigOverride {
        includeAsProperty = value
        return this
    }

    open fun setIgnorals(value: CirJsonIgnoreProperties.Value?): MutableConfigOverride {
        ignorals = value
        return this
    }

    open fun setNullHandling(value: CirJsonSetter.Value?): MutableConfigOverride {
        nullHandling = value
        return this
    }

    open fun setVisibility(value: CirJsonAutoDetect.Value?): MutableConfigOverride {
        visibility = value
        return this
    }

    open fun setIsIgnoredType(value: Boolean?): MutableConfigOverride {
        isIgnoredType = value
        return this
    }

    open fun setMergeable(value: Boolean?): MutableConfigOverride {
        mergeable = value
        return this
    }

}