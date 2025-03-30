package org.cirjson.cirjackson.core.type

import org.cirjson.cirjackson.core.CirJsonToken
import kotlin.reflect.KClass

/**
 * This is a simple value class used between core streaming and higher level databinding to pass information about type
 * IDs to write. Properties are exposed and mutable on purpose: they are only used for communication over serialization
 * of a single value, and neither retained across calls nor shared between threads.
 *
 * Usual usage pattern is such that instance of this class is passed on two calls that are needed for outputting type ID
 * (and possible additional wrapping, depending on format; CirJSON, for example, requires wrapping as type ID is part of
 * regular data): first, a "prefix" write (which usually includes actual ID), performed before value write; and then
 * matching "suffix" write after value serialization.
 *
 * @property forValue Object for which type ID is being written. Not needed by default handling, but may be useful for
 * customized format handling.
 *
 * @property valueShape Information about intended shape of the value being written (that is, [forValue]); in case of
 * structured values, start token of the structure; for scalars, value token. Main difference is between structured
 * values ([CirJsonToken.START_ARRAY], [CirJsonToken.START_OBJECT]) and scalars ([CirJsonToken.VALUE_STRING]): specific
 * scalar type may not be important for processing.
 *
 * @property id Actual type ID to use: usually [String].
 *
 * @property forValueType (optional) Super-type of [forValue] to use for type ID generation (if no explicit ID passed):
 * used instead of actual class of [forValue] in cases where we do not want to use the "real" type but something more
 * generic, usually to work around specific problem with implementation type, or its deserializer.
 */
open class WritableTypeID private constructor(val forValue: Any?, val valueShape: CirJsonToken, var id: Any?,
        var forValueType: KClass<*>?) {

    /**
     * If type id is to be embedded as a regular property, name of the property; otherwise `null`.
     *
     * NOTE: if "wrap-as-Object" is used, this does NOT contain property name to use but `null`.
     */
    var asProperty: String? = null

    /**
     * Property used to indicate style of inclusion for this type ID, in cases where no native type ID may be used
     * (either because format has none, like CirJSON; or because use of native type ids is disabled [with YAML]).
     */
    var inclusion: Inclusion? = null

    /**
     * Flag that can be set to indicate that wrapper structure was written (during prefix-writing); used to determine if
     * suffix requires matching close markers.
     */
    var isWrapperWritten = false

    /**
     * Optional additional information that generator may add during "prefix write", to be available on matching "suffix
     * write".
     */
    var extra: Any? = null

    /**
     * Constructor used when calling a method for writing type ID; caller knows value object, its intended shape as well
     * as id to use; but not details of wrapping (if any).
     *
     * @param forValue Actual value for which type information is written
     *
     * @param valueShape Serialize shape writer will use for value
     *
     * @param id Actual type id to use if known; `null` if not
     */
    constructor(forValue: Any?, valueShape: CirJsonToken, id: Any?) : this(forValue, valueShape, id, null)

    /**
     * Constructor used when calling a method for generating and writing Type ID, but where actual type to use for
     * generating ID is NOT the type of value (but its supertype).
     *
     * @param forValue Actual value for which type information is written
     *
     * @param forValueType Effective type of `forValue` to use for type ID generation
     *
     * @param valueShape Serialize shape writer will use for value
     */
    constructor(forValue: Any?, forValueType: KClass<*>?, valueShape: CirJsonToken) : this(forValue, valueShape, null,
            forValueType)

    /**
     * Constructor used when calling a method for generating and writing type ID; caller only knows value object and its
     * intended shape.
     *
     * @param forValue Actual value for which type information is written
     *
     * @param valueShape Serialize shape writer will use for value
     */
    constructor(forValue: Any?, valueShape: CirJsonToken) : this(forValue, valueShape, null)

    /**
     * Enumeration of values that matches enum `As` from annotation `CirJsonTypeInfo`: separate definition to avoid
     * dependency between streaming core and annotations packages; also allows more flexibility in case new values
     * needed at this level of internal API.
     *
     * NOTE: in most cases this only matters with formats that do NOT have native type ID capabilities, and require type
     * ID to be included within regular data (whether exposed as properties or not). Formats with native types usually
     * use native type id functionality regardless, unless overridden by a feature to use "non-native" type inclusion.
     */
    enum class Inclusion {

        /**
         * Inclusion as wrapper Array (1st element type ID, 2nd element value).
         *
         * Corresponds to `CirJsonTypeInfo.As.WRAPPER_ARRAY`.
         */
        WRAPPER_ARRAY,

        /**
         * Inclusion as wrapper Object that has one key/value pair where type ID is the key for typed value.
         *
         * Corresponds to `CirJsonTypeInfo.As.WRAPPER_OBJECT`.
         */
        WRAPPER_OBJECT,

        /**
         * Inclusion as a property within Object to write, but logically as separate metadata that is not exposed as
         * payload to caller: that is, does not match any of visible properties value object has.
         *
         * NOTE: if shape of typed value to write is NOT Object, will instead use [WRAPPER_ARRAY] inclusion.
         *
         * Corresponds to `CirJsonTypeInfo.As.PROPERTY`.
         */
        METADATA_PROPERTY,

        /**
         * Inclusion as a "regular" property within Object to write; this implies that its value should come from
         * regular POJO property on serialization, and be deserialized into such property. This handling, however, is up
         * to databinding.
         *
         * Regarding handling, type id is ONLY written as native type ID; if no native type IDs available, caller is
         * assumed to handle output some other way. This is different from [METADATA_PROPERTY].
         *
         * NOTE: if shape of typed value to write is NOT Object, will instead use [WRAPPER_ARRAY] inclusion.
         *
         * Corresponds to `CirJsonTypeInfo.As.EXISTING_PROPERTY`.
         */
        PAYLOAD_PROPERTY,

        /**
         * Inclusion as a property within "parent" Object of value Object to write. This typically requires slightly
         * convoluted processing in which property that contains type ID is actually written **after** typed value
         * object itself is written.
         *
         * Note that it is illegal to call write method if the current (parent) write context is not Object: no coercion
         * is done for other inclusion types (unlike with other `xxx_PROPERTY` choices). This also means that root
         * values MAY NOT use this type id inclusion mechanism (as they have no parent context).
         *
         * Corresponds to `CirJsonTypeInfo.As.EXTERNAL_PROPERTY`.
         */
        PARENT_PROPERTY;

        val isRequiringObjectContext
            get() = this == METADATA_PROPERTY || this == PAYLOAD_PROPERTY

    }

}