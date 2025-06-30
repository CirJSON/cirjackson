package org.cirjson.cirjackson.databind.configuration

/**
 * Mutable version of [CoercionConfig] (or rather, extended API) exposed during configuration phase of
 * [org.cirjson.cirjackson.databind.ObjectMapper] (via [org.cirjson.cirjackson.databind.cirjson.CirJsonMapper.builder]).
 */
open class MutableCoercionConfig : CoercionConfig {

    constructor() : super()

    protected constructor(source: CoercionConfig) : super(source)

    open fun copy(): MutableCoercionConfig {
        return MutableCoercionConfig(this)
    }

    /**
     * Method to set coercions to target type or class during builder-style mapper construction with
     *
     * * [MapperBuilder.withCoercionConfig] (with [kotlin.reflect.KClass] and `(MutableCoercionConfig) -> Unit`
     * arguments),
     *
     * * [MapperBuilder.withCoercionConfig] (with [org.cirjson.cirjackson.databind.type.LogicalType] and
     * `(MutableCoercionConfig) -> Unit` arguments),
     *
     * * [MapperBuilder.withCoercionConfigDefaults]
     *
     * ... these builder methods. Refrain from using this method outside of builder phase.
     */
    open fun setCoercion(shape: CoercionInputShape, action: CoercionAction): MutableCoercionConfig {
        myCoercionsByShape[shape.ordinal] = action
        return this
    }

    open fun setAcceptBlankAsEmpty(state: Boolean?): MutableCoercionConfig {
        acceptBlankAsEmpty = state
        return this
    }

}