package org.cirjson.cirjackson.databind.configuration

/**
 * Set of possible actions for requested coercion from an input shape [CoercionInputShape] that does not directly or
 * naturally match the target type ([org.cirjson.cirjackson.databind.type.LogicalType]). This action is a suggestion for
 * deserializers to use in cases where alternate actions could be appropriate: it is up to deserializer to check
 * configured action and take it into consideration.
 */
enum class CoercionAction {

    /**
     * Action to fail the coercion attempt with exception
     */
    Fail,

    /**
     * Action to attempt coercion (which may lead to failure)
     */
    TryConvert,

    /**
     * Action to convert to `null` value
     */
    AsNull,

    /**
     * Action to convert to "empty" value for type, whatever that is: for primitive types and their wrappers this is
     * "default" value (for example, for `int` that would be `0`); for [Collections][Collection] empty collection; for
     * POJOs instance configured with default constructor and so on.
     */
    AsEmpty

}