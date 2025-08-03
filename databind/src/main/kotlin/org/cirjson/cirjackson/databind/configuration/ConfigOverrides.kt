package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.CirJsonAutoDetect
import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.annotations.CirJsonSetter
import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.introspection.VisibilityChecker
import java.util.*
import kotlin.reflect.KClass

/**
 * Container for individual [ConfigOverride] values.
 */
open class ConfigOverrides protected constructor(
        protected var myOverrides: MutableMap<KClass<*>, MutableConfigOverride>?,
        protected var myDefaultInclusion: CirJsonInclude.Value, defaultNullHandling: CirJsonSetter.Value,
        defaultVisibility: VisibilityChecker, defaultMergeable: Boolean?, defaultLeniency: Boolean?) :
        Snapshottable<ConfigOverrides> {

    var defaultNullHandling = defaultNullHandling
        protected set

    var defaultVisibility = defaultVisibility
        protected set

    var defaultMergeable = defaultMergeable
        protected set

    var defaultLeniency = defaultLeniency
        protected set

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor() : this(null, INCLUDE_DEFAULT, CirJsonSetter.Value.EMPTY, DEFAULT_VISIBILITY_CHECKER, null, null)

    override fun snapshot(): ConfigOverrides {
        val newOverrides = if (myOverrides == null) {
            null
        } else {
            HashMap<KClass<*>, MutableConfigOverride>().apply {
                for (entry in myOverrides!!.entries) {
                    this[entry.key] = entry.value.copy()
                }
            }
        }

        return ConfigOverrides(newOverrides, myDefaultInclusion, defaultNullHandling, defaultVisibility,
                defaultMergeable,
                defaultLeniency)
    }

    /*
     *******************************************************************************************************************
     * Per-type override access
     *******************************************************************************************************************
     */

    open fun findOverride(type: KClass<*>): ConfigOverride? {
        return myOverrides?.get(type)
    }

    open fun findOrCreateOverride(type: KClass<*>): MutableConfigOverride {
        val overrides = myOverrides ?: HashMap<KClass<*>, MutableConfigOverride>().also { myOverrides = it }
        return overrides[type] ?: MutableConfigOverride().also { overrides[type] = it }
    }

    /**
     * Specific accessor for finding [CirJsonFormat.Value] for given type, considering global default for leniency as
     * well as per-type format override (if any).
     *
     * @return Default format settings for type; never `null`.
     */
    open fun findFormatDefaults(type: KClass<*>): CirJsonFormat.Value {
        if (myOverrides != null) {
            val override = myOverrides!![type]

            if (override != null) {
                val format = override.format

                if (format != null) {
                    return format.takeIf { it.hasLenient() } ?: format.withLenient(defaultLeniency)
                }
            }
        }

        if (defaultLeniency == null) {
            return CirJsonFormat.Value.EMPTY
        }

        return CirJsonFormat.Value.forLeniency(defaultLeniency)
    }

    /*
     *******************************************************************************************************************
     * Global defaults accessors
     *******************************************************************************************************************
     */

    open val defaultInclusion: CirJsonInclude.Value?
        get() = myDefaultInclusion

    /**
     * Alternate accessor needed due to complexities of Record auto-discovery: needs to obey custom overrides but also
     * give alternate "default" if no customizations made.
     */
    open val defaultRecordVisibility: VisibilityChecker
        get() = defaultVisibility.takeUnless { it == DEFAULT_VISIBILITY_CHECKER } ?: DEFAULT_RECORD_VISIBILITY_CHECKER

    /*
     *******************************************************************************************************************
     * Global defaults mutators
     *******************************************************************************************************************
     */

    open fun setDefaultInclusion(value: CirJsonInclude.Value): ConfigOverrides {
        myDefaultInclusion = value
        return this
    }

    open fun setDefaultNullHandling(value: CirJsonSetter.Value): ConfigOverrides {
        defaultNullHandling = value
        return this
    }

    open fun setDefaultVisibility(value: VisibilityChecker): ConfigOverrides {
        defaultVisibility = value
        return this
    }

    open fun setDefaultVisibility(value: CirJsonAutoDetect.Value?): ConfigOverrides {
        defaultVisibility = VisibilityChecker.construct(value)
        return this
    }

    open fun setDefaultMergeable(value: Boolean?): ConfigOverrides {
        defaultMergeable = value
        return this
    }

    open fun setDefaultLeniency(value: Boolean?): ConfigOverrides {
        defaultLeniency = value
        return this
    }

    /*
     *******************************************************************************************************************
     * Standard methods (for diagnostics)
     *******************************************************************************************************************
     */

    override fun toString(): String {
        val stringBuilder =
                StringBuilder("[ConfigOverrides ").append("incl=").append(myDefaultInclusion).append(", nulls=")
                        .append(defaultNullHandling).append(", merge=").append(defaultMergeable).append(", leniency=")
                        .append(defaultLeniency).append(", visibility=").append(defaultVisibility).append(", typed=")

        if (myOverrides == null) {
            stringBuilder.append("NULL")
        } else {
            stringBuilder.append('(').append(myOverrides!!.size).append("){")
            val sorted = TreeMap<String, MutableConfigOverride>()
            myOverrides!!.forEach { key, value -> sorted[key.qualifiedName!!] = value }
            sorted.forEach { key, value -> stringBuilder.append("'$key'->$value") }
            stringBuilder.append('}')
        }

        return stringBuilder.append(']').toString()
    }

    companion object {

        /**
         * Convenience value used as the default root setting. Note that although in a way it would make sense to use
         * "ALWAYS" for both, problems arise in some cases where default is seen as explicit setting, overriding
         * possible per-class annotation; hence use of "USE_DEFAULTS".
         */
        internal val INCLUDE_DEFAULT =
                CirJsonInclude.Value.construct(CirJsonInclude.Include.USE_DEFAULTS, CirJsonInclude.Include.USE_DEFAULTS)

        private val DEFAULT_VISIBILITY_CHECKER = VisibilityChecker.DEFAULT

        private val DEFAULT_RECORD_VISIBILITY_CHECKER = VisibilityChecker.ALL_PUBLIC_EXCEPT_CREATORS

    }

}