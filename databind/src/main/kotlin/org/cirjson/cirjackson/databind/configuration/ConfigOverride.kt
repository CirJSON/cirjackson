package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.*

/**
 * Configuration object that is accessed by databinding functionality to find overrides to configuration of properties,
 * based on the declared type of the property. Such overrides have precedence over annotations attached to actual type
 * ([kotlin.reflect.KClass]), but can be further overridden by annotations attached to the property itself.
 */
abstract class ConfigOverride {

    /**
     * Definitions of format overrides, if any.
     */
    var format: CirJsonFormat.Value?
        protected set

    /**
     * Definitions of inclusion defaults to use for properties included in this POJO type. Overrides global defaults may
     * be overridden by per-property-type (see [includeAsProperty]) and per-property overrides (annotations).
     */
    var include: CirJsonInclude.Value?
        protected set

    /**
     * Definitions of inclusion defaults for properties of this specified type (regardless of POJO in which they are
     * included). Overrides global defaults, per-POJO inclusion defaults (see [include]), may be overridden by
     * per-property overrides.
     */
    var includeAsProperty: CirJsonInclude.Value?
        protected set

    /**
     * Definitions of property ignoral (whether to serialize, deserialize given logical property) overrides, if any.
     */
    var ignorals: CirJsonIgnoreProperties.Value?
        protected set

    /**
     * Definitions of setter overrides regarding `null` handling
     */
    var nullHandling: CirJsonSetter.Value?
        protected set

    /**
     * Overrides for auto-detection visibility rules for this type.
     */
    var visibility: CirJsonAutoDetect.Value?
        protected set

    /**
     * Flag that indicates whether "is ignorable type" is specified for this type; and if so, is it to be ignored
     * (`true`) or not ignored (`false`); `null` is used to indicate "not specified", in which case other configuration
     * (class annotation) is used.
     */
    var isIgnoredType: Boolean?
        protected set

    /**
     * Flag that indicates whether properties of this type default to being merged or not.
     */
    var mergeable: Boolean?
        protected set

    protected constructor() {
        format = null
        include = null
        includeAsProperty = null
        ignorals = null
        nullHandling = null
        visibility = null
        isIgnoredType = null
        mergeable = null
    }

    protected constructor(source: ConfigOverride) {
        format = source.format
        include = source.include
        includeAsProperty = source.includeAsProperty
        ignorals = source.ignorals
        nullHandling = source.nullHandling
        visibility = source.visibility
        isIgnoredType = source.isIgnoredType
        mergeable = source.mergeable
    }

    val formatOrEmpty: CirJsonFormat.Value
        get() = format ?: CirJsonFormat.Value.EMPTY

    override fun toString(): String {
        val stringBuilder = StringBuilder("[ConfigOverrides ")
        stringBuilder.append("format=").append(format)
        stringBuilder.append(", include=").append(include).append('/').append(includeAsProperty)
        stringBuilder.append(", ignorals=").append(ignorals)
        stringBuilder.append(", nullHandling=").append(nullHandling)
        stringBuilder.append(", visibility=").append(visibility)
        stringBuilder.append(", isIgnoredType=").append(isIgnoredType)
        stringBuilder.append(", mergeable=").append(mergeable)
        stringBuilder.append(']')
        return stringBuilder.toString()
    }

    /**
     * Implementation used solely for "empty" instance; has no mutators and is not changed by core functionality.
     */
    private object Empty : ConfigOverride()

    companion object {

        /**
         * Accessor for immutable "empty" instance that has no configuration overrides defined.
         */
        fun empty(): ConfigOverride {
            return Empty
        }

    }

}