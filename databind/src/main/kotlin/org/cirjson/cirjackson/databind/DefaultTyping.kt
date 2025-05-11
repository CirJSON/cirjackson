package org.cirjson.cirjackson.databind

/**
 * Enumeration used with `CirJsonMapper.defaultTyping()` methods to specify what kind of types (classes) default typing
 * should be used for. It will only be used if no explicit type information is found, but this enumeration further
 * limits subset of those types.
 */
enum class DefaultTyping {

    /**
     * This value means that only properties that have [Any] as declared type (including generic types without the
     * explicit type) will use default typing.
     */
    OBJECT,

    /**
     * Value that means that default typing will be used for properties with declared type of [Any] or an abstract type
     * (abstract class or interface). Note that this does **not** include array types. This does NOT apply to
     * [org.cirjson.cirjackson.core.TreeNode] and its subtypes.
     */
    OBJECT_AND_NON_CONCRETE,

    /**
     * Value that means that default typing will be used for all types covered by [OBJECT_AND_NON_CONCRETE], plus all
     * array types for them. This does NOT apply to [org.cirjson.cirjackson.core.TreeNode] and its subtypes.
     */
    NON_CONCRETE_AND_ARRAYS,

    /**
     * Value that means that default typing will be used for all non-final types, as well as for all arrays of non-final
     * types. The exception is for a small number of "natural" types (String, Boolean, Int, Double), which can be
     * correctly inferred from CirJSON. This does NOT apply to [org.cirjson.cirjackson.core.TreeNode] and its subtypes.
     */
    NON_FINAL,

    /**
     * Enables default typing for non-final types as [NON_FINAL], but also includes Enums.
     */
    NON_FINAL_AND_ENUMS

}