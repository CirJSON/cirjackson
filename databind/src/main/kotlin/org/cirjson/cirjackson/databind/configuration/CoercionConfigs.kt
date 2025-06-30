package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.DeserializationFeature
import org.cirjson.cirjackson.databind.MapperFeature
import org.cirjson.cirjackson.databind.type.LogicalType
import kotlin.reflect.KClass

/**
 * @property myDefaultAction Global default for cases which are not explicitly covered
 *
 * @property myDefaultCoercions Default coercion definitions used if no overrides found by logical or physical type.
 *
 * @property myPerTypeCoercions Coercion definitions by logical type ([LogicalType])
 *
 * @property myPerClassCoercions Coercion definitions by physical type (KClass).
 */
open class CoercionConfigs protected constructor(protected var myDefaultAction: CoercionAction,
        protected val myDefaultCoercions: MutableCoercionConfig,
        protected var myPerTypeCoercions: Array<MutableCoercionConfig?>?,
        protected var myPerClassCoercions: MutableMap<KClass<*>, MutableCoercionConfig>?) :
        Snapshottable<CoercionConfigs> {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor() : this(CoercionAction.TryConvert, MutableCoercionConfig(), null, null)

    /**
     * Method called to create a non-shared copy of configuration settings, to be used by another
     * [org.cirjson.cirjackson.databind.ObjectMapper] instance.
     *
     * @return A non-shared copy of configuration settings
     */
    override fun snapshot(): CoercionConfigs {
        val newPerType = myPerTypeCoercions?.let { Array(it.size) { i -> copy(it[i]) } }
        val newPerClass = myPerClassCoercions?.let {
            HashMap<KClass<*>, MutableCoercionConfig>().apply {
                for (entry in it.entries) {
                    this[entry.key] = entry.value.copy()
                }
            }
        }
        return CoercionConfigs(myDefaultAction, myDefaultCoercions.copy(), newPerType, newPerClass)
    }

    /*
     *******************************************************************************************************************
     * Mutators: global defaults
     *******************************************************************************************************************
     */

    open fun defaultCoercions(): MutableCoercionConfig {
        return myDefaultCoercions
    }

    /*
     *******************************************************************************************************************
     * Mutators: per type
     *******************************************************************************************************************
     */

    open fun findOrCreateCoercion(type: LogicalType): MutableCoercionConfig {
        val perTypeCoercions = myPerTypeCoercions ?: arrayOfNulls<MutableCoercionConfig>(TARGET_TYPE_COUNT).also {
            myPerTypeCoercions = it
        }
        return perTypeCoercions[type.ordinal] ?: MutableCoercionConfig().also { perTypeCoercions[type.ordinal] = it }
    }

    open fun findOrCreateCoercion(type: KClass<*>): MutableCoercionConfig {
        val perClassCoercions =
                myPerClassCoercions ?: HashMap<KClass<*>, MutableCoercionConfig>().also { myPerClassCoercions = it }
        return perClassCoercions[type] ?: MutableCoercionConfig().also { perClassCoercions[type] = it }
    }

    /*
     *******************************************************************************************************************
     * Access
     *******************************************************************************************************************
     */

    /**
     * General-purpose accessor for finding what to do when specified coercion from shape that is now always allowed to
     * be coerced from is requested.
     *
     * @param config Currently active deserialization configuration
     *
     * @param targetType Logical target type of coercion
     *
     * @param targetClass Physical target type of coercion
     *
     * @param inputShape Input shape to coerce from
     *
     * @return CoercionAction configured for specified coercion
     */
    open fun findCoercion(config: DeserializationConfig, targetType: LogicalType?, targetClass: KClass<*>?,
            inputShape: CoercionInputShape): CoercionAction {
        if (myPerClassCoercions != null && targetClass != null) {
            val coercionConfig = myPerClassCoercions!![targetClass]

            if (coercionConfig != null) {
                val action = coercionConfig.findAction(inputShape)

                if (action != null) {
                    return action
                }
            }
        }

        if (myPerTypeCoercions != null && targetType != null) {
            val coercionConfig = myPerTypeCoercions!![targetType.ordinal]

            if (coercionConfig != null) {
                val action = coercionConfig.findAction(inputShape)

                if (action != null) {
                    return action
                }
            }
        }

        val action = myDefaultCoercions.findAction(inputShape)

        if (action != null) {
            return action
        }

        when (inputShape) {
            CoercionInputShape.EmptyArray -> {
                return if (config.isEnabled(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT)) {
                    CoercionAction.AsNull
                } else {
                    CoercionAction.Fail
                }
            }

            CoercionInputShape.Float -> {
                if (targetType == LogicalType.INTEGER) {
                    return if (config.isEnabled(DeserializationFeature.ACCEPT_FLOAT_AS_INT)) {
                        CoercionAction.TryConvert
                    } else {
                        CoercionAction.Fail
                    }
                }
            }

            CoercionInputShape.Integer -> {
                if (targetType == LogicalType.ENUM) {
                    if (config.isEnabled(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)) {
                        return CoercionAction.Fail
                    }
                }
            }

            else -> {}
        }

        val baseScalar = isScalarType(targetType)

        if (baseScalar && config.isEnabled(MapperFeature.ALLOW_COERCION_OF_SCALARS) &&
                (targetType != LogicalType.FLOAT || inputShape != CoercionInputShape.Integer)) {
            return CoercionAction.Fail
        }

        if (inputShape != CoercionInputShape.EmptyString) {
            return myDefaultAction
        }

        if (targetType == LogicalType.OTHER_SCALAR) {
            return CoercionAction.TryConvert
        }

        if (baseScalar || config.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)) {
            return CoercionAction.AsNull
        }

        return CoercionAction.Fail
    }

    /**
     * More specialized accessor called in case of input being a blank String (one consisting of only white space
     * characters with length of at least one). Will basically first determine if "blank as empty" is allowed: if not,
     * returns `actionIfBlankNotAllowed`, otherwise returns action for [CoercionInputShape.EmptyString].
     *
     * @param config Currently active deserialization configuration
     *
     * @param targetType Logical target type of coercion
     *
     * @param targetClass Physical target type of coercion
     *
     * @param actionIfBlankNotAllowed Return value to use in case "blanks as empty" is not allowed
     *
     * @return CoercionAction configured for specified coercion from blank string
     */
    open fun findCoercionFromBlankString(config: DeserializationConfig, targetType: LogicalType?,
            targetClass: KClass<*>?, actionIfBlankNotAllowed: CoercionAction): CoercionAction {
        var acceptBlankAsEmpty: Boolean? = null
        var action: CoercionAction? = null

        if (myPerClassCoercions != null && targetClass != null) {
            val coercionConfig = myPerClassCoercions!![targetClass]

            if (coercionConfig != null) {
                acceptBlankAsEmpty = coercionConfig.acceptBlankAsEmpty
                action = coercionConfig.findAction(CoercionInputShape.EmptyString)
            }
        }

        if (myPerTypeCoercions != null && targetType != null) {
            val coercionConfig = myPerTypeCoercions!![targetType.ordinal]

            if (coercionConfig != null) {
                if (acceptBlankAsEmpty == null) {
                    acceptBlankAsEmpty = coercionConfig.acceptBlankAsEmpty
                }

                if (action == null) {
                    action = coercionConfig.findAction(CoercionInputShape.EmptyString)
                }
            }
        }

        if (acceptBlankAsEmpty == null) {
            acceptBlankAsEmpty = myDefaultCoercions.acceptBlankAsEmpty
        }

        if (action == null) {
            action = myDefaultCoercions.findAction(CoercionInputShape.EmptyString)
        }

        if (acceptBlankAsEmpty == false) {
            return actionIfBlankNotAllowed
        }

        if (action != null) {
            return action
        }

        if (isScalarType(targetType)) {
            return CoercionAction.AsNull
        }

        if (config.isEnabled(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT)) {
            return CoercionAction.AsNull
        }

        return actionIfBlankNotAllowed
    }

    protected open fun isScalarType(type: LogicalType?): Boolean {
        return type == LogicalType.FLOAT || type == LogicalType.INTEGER || type == LogicalType.BOOLEAN ||
                type == LogicalType.DATE_TIME
    }

    companion object {

        /*
         ***************************************************************************************************************
         * Lifecycle
         ***************************************************************************************************************
         */

        private val TARGET_TYPE_COUNT = LogicalType.entries.size

        private fun copy(source: MutableCoercionConfig?): MutableCoercionConfig? {
            return source?.copy()
        }

    }

}