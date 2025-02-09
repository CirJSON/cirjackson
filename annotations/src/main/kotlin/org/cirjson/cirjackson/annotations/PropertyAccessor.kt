package org.cirjson.cirjackson.annotations

/**
 * Enumeration used to define kinds of elements (called "property accessors") that annotations like [CirJsonAutoDetect]
 * apply to.
 *
 * In addition to method types (GETTER/IS_GETTER, SETTER, CREATOR, SCALAR_CONSTRUCTOR) and the field type (FIELD), 2
 * pseudo-types are defined for convenience: `ALWAYS` and `NONE`. These can be used to indicate, all or none of
 * available method types (respectively), for use by annotations that takes `CirJsonMethod` argument.
 */
enum class PropertyAccessor {

    /**
     * Getters are methods used to get a POJO field value for serialization, or, under certain conditions also for
     * de-serialization. Latter can be used for effectively setting Collection or Map values in absence of setters, if
     * returned value is not a copy but actual value of the logical property.
     */
    GETTER,

    /**
     * Setters are methods used to set a POJO value for deserialization.
     */
    SETTER,

    /**
     * Field refers to fields of regular objects.
     */
    FIELD,

    /**
     * "Is getters" are getter-like methods that are named "isXxx" (instead of "getXxx" for getters) and return boolean
     * value (either primitive, or [Boolean]).
     */
    IS_GETTER,

    /**
     * Creators are constructors and (static) factory methods used to construct POJO instances for deserialization, not
     * including single-scalar-argument constructors (for which `SCALAR_CONSTRUCTOR` is used).
     */
    CREATOR,

    /**
     * Scalar constructors are special case creators: constructors that take just one scalar argument of one types
     * `Int`, `Long`, `Boolean`, `Double` or `String`.
     */
    SCALAR_CONSTRUCTOR,

    /**
     * This pseudo-type indicates that none of accessors if affected.
     */
    NONE,

    /**
     * This pseudo-type indicates that all accessors are affected.
     */
    ALL;

    val isCreatorEnabled: Boolean
        get() = this == ALL || this == CREATOR

    val isScalarConstructorEnabled: Boolean
        get() = this == ALL || this == SCALAR_CONSTRUCTOR

    val isGetterEnabled: Boolean
        get() = this == ALL || this == GETTER

    val isIsGetterEnabled: Boolean
        get() = this == ALL || this == IS_GETTER

    val isSetterEnabled: Boolean
        get() = this == ALL || this == SETTER

    val isFieldEnabled: Boolean
        get() = this == ALL || this == FIELD

}