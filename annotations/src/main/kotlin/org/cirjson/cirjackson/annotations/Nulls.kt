package org.cirjson.cirjackson.annotations

/**
 * Enumeration used with [CirJsonSetter] (for properties `nulls` and `contentNulls`) to define how explicit `null`
 * values from input (if input format has the concept; CirJSON, for example does) are handled.
 */
enum class Nulls {

    /**
     * Value that indicates that an input null should result in assignment of `null` value of matching property (except
     * where deserializer indicates other "null value" by overriding `getNullValue(...)` method)
     */
    SET,

    /**
     * Value that indicates that an input null value should be skipped and no assignment is to be made; this usually
     * means that the property will have its default value.
     */
    SKIP,

    /**
     * Value that indicates that an exception (of type that indicates input mismatch problem) is to be thrown, to
     * indicate that null values are not accepted.
     */
    FAIL,

    /**
     * Value that indicates that value to assign should come from the value deserializer of the type, using method
     * `emptyValue`.
     */
    AS_EMPTY,

    /**
     * Pseudo-value used to indicate that defaults are to be used for handling, that is, this value specifies no
     * explicit handling override.
     */
    DEFAULT

}