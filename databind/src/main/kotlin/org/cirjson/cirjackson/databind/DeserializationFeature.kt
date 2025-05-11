package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.databind.configuration.ConfigFeature

/**
 * Enumeration that defines simple on/off features that affect the way objects are deserialized from CirJSON
 *
 * Note that features can be set both through [ObjectMapper] (as sort of defaults) and through [ObjectReader]. In the
 * first case, these defaults must follow "config-then-use" patterns (i.e., defined once, not changed afterward); all
 * per-call changes must be done using [ObjectReader].
 */
enum class DeserializationFeature(override val isEnabledByDefault: Boolean) : ConfigFeature {

    /*
     *******************************************************************************************************************
     * Value (mostly scalar) mapping features
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whetherCir JSON floating point numbers are to be deserialized into
     * [BigDecimals][java.math.BigDecimal] if only generic type description (either [Any] or [Number], or within untyped
     * [Map] or [Collection] context) is available. If enabled, such values will be deserialized as
     * [BigDecimals][java.math.BigDecimal]; if disabled, will be deserialized as [Doubles][Double].
     *
     * NOTE: one related aspect of [java.math.BigDecimal] handling that may need configuring is whether trailing zeroes
     * are trimmed: [org.cirjson.cirjackson.databind.configuration.CirJsonNodeFeature.STRIP_TRAILING_BIG_DECIMAL_ZEROES]
     * is used for optionally enabling this for [CirJsonNode] values.
     *
     * The feature is disabled by default, meaning that "untyped" floating point numbers will by default be deserialized
     * as [Doubles][Double] (choice is for performance reason -- BigDecimals are slower than Doubles).
     */
    USE_BIG_DECIMAL_FOR_FLOATS(false),

    /**
     * Feature that determines whether CirJSON integral (non-floating-point) numbers are to be deserialized into
     * [BigIntegers][java.math.BigInteger] if only generic type description (either [Object] or [Number], or within
     * untyped [Map] or [Collection] context) is available. If enabled, such values will be deserialized as
     * [BigIntegers][java.math.BigInteger]; if disabled, will be deserialized as "smallest" available type, which is
     * either [Int], [Long] or [java.math.BigInteger], depending on the number of digits.
     *
     * The feature is disabled by default, meaning that "untyped" integral numbers will by default be deserialized using
     * whatever is the most compact integral type, to optimize efficiency.
     */
    USE_BIG_INTEGER_FOR_INTS(false),

    /**
     * Feature that determines how "small" CirJSON integral (non-floating-point) numbers -- ones that fit in 32-bit
     * signed integer (`int`) -- are bound when the target type is loosely typed as [Any] or [Number] (or within untyped
     * [Map] or [Collection] context). If enabled, such values will be deserialized as [Long]; if disabled, they will be
     * deserialized as "smallest" available type, [Int].
     *
     * Note: if [USE_BIG_INTEGER_FOR_INTS] is enabled, it has precedence over this setting, forcing use of
     * [java.math.BigInteger] for all integral values.
     *
     * The feature is disabled by default, meaning that "untyped" integral numbers will by default be deserialized using
     * [Int] if value fits.
     */
    USE_LONG_FOR_INTS(false),

    /**
     * Feature that determines whether CirJSON Array is mapped to `Array<Any>` or `List<Any>` when binding "untyped"
     * objects (ones with the nominal type of `Any`). If true, it binds as `Array<Any>`; if false, as `List<Any>`.
     *
     * The feature is disabled by default, meaning that CirJSON arrays are bound as [Lists][List].
     */
    USE_JAVA_ARRAY_FOR_CIRJSON_ARRAY(false),

    /*
     *******************************************************************************************************************
     * Error handling features
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whether encountering of unknown properties (ones that do not map to a property, and there
     * is no "any setter" or handler that can handle it) should result in a failure (by throwing a [DatabindException])
     * or not. This setting only takes effect after all other handling methods for unknown properties have been tried,
     * and the property remains unhandled.
     *
     * The feature is enabled by default (meaning that a [DatabindException] will be thrown if an unknown property is
     * encountered).
     */
    FAIL_ON_UNKNOWN_PROPERTIES(true),

    /**
     * Feature that determines whether encountering of CirJSON `null` is an error when deserializing into primitive
     * types (like Int or Double). If it is, a [DatabindException] is thrown to indicate this; if not, default value is
     * used (`0` for Int, `0.0` for Double, same defaulting as what JVM uses).
     *
     * The feature is disabled by default.
     */
    FAIL_ON_NULL_FOR_PRIMITIVES(false),

    /**
     * Feature that determines whether CirJSON integer numbers are valid values to be used for deserializing enum
     * values. If it is set to `false`, numbers are acceptable and are used to map to `ordinal` of matching enumeration
     * value; if `true`, numbers are not allowed and a [DatabindException] will be thrown. Latter behavior makes sense
     * if there is concern that accidental mapping from integer values to enums might happen (and when enums are always
     * serialized as CirJSON Strings)
     *
     * The feature is disabled by default.
     */
    FAIL_ON_NUMBERS_FOR_ENUMS(false),

    /**
     * Feature that determines what happens when the type of polymorphic value (indicated, for example, by
     * [org.cirjson.cirjackson.annotations.CirJsonTypeInfo]) cannot be found (missing) or resolved (invalid class name,
     * non-mappable id); if enabled, an exception is thrown; if false, `null` value is used instead.
     *
     * The feature is enabled by default so that exception is thrown for missing or invalid type information.
     */
    FAIL_ON_INVALID_SUBTYPE(true),

    /**
     * Feature that determines what happens when reading CirJSON content into a tree
     * ([org.cirjson.cirjackson.core.TreeNode]) and a duplicate key is encountered (property name that was already seen
     * for the CirJSON Object). If enabled, [DatabindException] will be thrown; if disabled, no exception is thrown and
     * the new (later) value overwrites the earlier value.
     *
     * Note that this property does NOT affect other aspects of data-binding; that is, no detection is done with respect
     * to POJO properties or [Map] keys. New features may be added to control additional cases.
     *
     * The feature is disabled by default so that no exception is thrown.
     */
    FAIL_ON_READING_DUP_TREE_KEY(false),

    /**
     * Feature that determines what happens when a property that has been explicitly marked as ignorable is encountered
     * in input: if feature is enabled, [DatabindException] is thrown; if false, property is quietly skipped.
     *
     * The feature is disabled by default so that no exception is thrown.
     */
    FAIL_ON_IGNORED_PROPERTIES(false),

    /**
     * Feature that determines what happens if an Object ID reference is encountered that does not refer to an actual
     * Object with that id ("unresolved Object ID"): either an exception
     * [org.cirjson.cirjackson.databind.deserialization.UnresolvedForwardReferenceException] containing information
     * about [org.cirjson.cirjackson.databind.deserialization.UnresolvedId] is thrown (`true`), or a `null` object is
     * used instead (`false`). Note that if this is set to `false`, no further processing is done; specifically, if
     * reference is defined via setter method, that method will NOT be called.
     *
     * The feature is enabled by default, so that unknown Object Ids will result in an exception thrown at the end of
     * deserialization.
     */
    FAIL_ON_UNRESOLVED_OBJECT_IDS(true),

    /**
     * Feature that determines what happens if one or more Creator properties (properties bound to parameters of Creator
     * method (constructor or static factory method)) are missing value to bind to from content. If enabled, such
     * missing values result in a [DatabindException] being thrown with information on the first one (by index) of
     * missing properties. If disabled, and if property is NOT marked as required, missing Creator properties are filled
     * with `null values` provided by deserializer for the type of parameter (usually `null` for Object types, and
     * default value for primitives; but redefinable via custom deserializers).
     *
     * Note that having an injectable value counts as "not missing".
     *
     * The feature is disabled by default, so that no exception is thrown for missing creator property values, unless
     * they are explicitly marked as `required`.
     */
    FAIL_ON_MISSING_CREATOR_PROPERTIES(false),

    /**
     * Feature that determines what happens if one or more Creator properties (properties bound to parameters of Creator
     * method (constructor or static factory method)) are bound to `null` values - either from the CirJSON or as a
     * default value. This is useful if you want to avoid `nulls` in your codebase, and particularly useful if you are
     * using optionals for non-mandatory fields.
     *
     * The feature is disabled by default, so that no exception is thrown for missing creator property values, unless
     * they are explicitly marked as `required`.
     */
    FAIL_ON_NULL_CREATOR_PROPERTIES(false),

    /**
     * Feature that determines what happens when a property annotated with
     * [org.cirjson.cirjackson.annotations.CirJsonTypeInfo.As.EXTERNAL_PROPERTY] is missing, but associated type id is
     * available. If enabled, a [DatabindException] is always thrown when property value is missing (if type id does
     * exist); if disabled, exception is only thrown if property is marked as `required`.
     *
     * The feature is enabled by default, so that exception is thrown when a subtype property is missing.
     */
    FAIL_ON_MISSING_EXTERNAL_TYPE_ID_PROPERTY(true),

    /**
     * Feature that determines behavior for data-binding after binding the root value. If the feature is enabled, one
     * more call to [org.cirjson.cirjackson.core.CirJsonParser.nextToken] is made to ensure that no more tokens are
     * found (and if any is found, [org.cirjson.cirjackson.databind.exception.MismatchedInputException] is thrown); if
     * disabled, no further checks are made.
     *
     * Feature could alternatively be called `READ_FULL_STREAM`, since it effectively verifies that input stream
     * contains only as much data as is needed for binding the full value, and nothing more (except for possible
     * ignorable white space or comments, if supported by data format).
     *
     * The feature is disabled by default (so that no check is made for possible trailing token(s)) for backwards
     * compatibility reasons.
     */
    FAIL_ON_TRAILING_TOKENS(false),

    /**
     * Feature that determines whether CirJackson code should catch and wrap non-CirJackson [Exceptions][Exception] (but
     * never [Errors][Error]!) to add additional information about the location (within input) of the problem or not. If
     * enabled, most exceptions will be caught and re-thrown; this can be convenient both in that all exceptions will be
     * checked and declared, and so there is more contextual information. However, sometimes calling application may
     * just want "raw" unchecked exceptions passed as is.
     *
     * NOTE: most of the time exceptions that may or may not be wrapped are of type [RuntimeException]: as mentioned
     * earlier, [CirJacksonExceptions][org.cirjson.cirjackson.core.CirJacksonException]) will always be passed as-is.
     *
     * Disabling this feature will mean that you will need to adjust your try/catch blocks to properly handle
     * [RuntimeExceptions][RuntimeException]. Failing to do so may cause your application to crash due to unhandled
     * exceptions.
     *
     * The feature is enabled by default.
     */
    WRAP_EXCEPTIONS(true),

    /**
     * Feature that determines the handling of properties not included in the active CirJSON view during
     * deserialization.
     *
     * When enabled, if a property is encountered during deserialization that is not part of the active view (as defined
     * by [org.cirjson.cirjackson.annotations.CirJsonView]).
     *
     * This feature is particularly useful in scenarios where strict adherence to the specified view is required and any
     * deviation, such as the presence of properties not belonging to the view, should be reported as an error. It
     * enhances the robustness of data binding by ensuring that only the properties relevant to the active view are
     * considered during deserialization, thereby preventing unintended data from being processed.
     *
     * The feature is enabled by default.
     */
    FAIL_ON_UNEXPECTED_VIEW_PROPERTIES(true),

    /*
     *******************************************************************************************************************
     * Structural conversion features
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whether it is acceptable to coerce non-array (in CirJSON) values to work with collection
     * (arrays, Collection) types. If enabled, collection deserializers will try to handle non-array values as if they
     * had "implicit" surrounding CirJSON array. This feature is meant to be used for compatibility/interoperability
     * reasons, to work with packages (such as XML-to-CirJSON converters) that leave out CirJSON array in cases where
     * there is just a single element in the array.
     *
     * The feature is disabled by default.
     */
    ACCEPT_SINGLE_VALUE_AS_ARRAY(false),

    /**
     * Feature that determines whether it is acceptable to coerce a single value array (in CirJSON) values to the
     * corresponding value type. This is basically the opposite of the [ACCEPT_SINGLE_VALUE_AS_ARRAY] feature. If more
     * than one value is found in the array, a [DatabindException] is thrown.
     *
     * NOTE: only **single** wrapper Array is allowed: if multiple attempted, an exception will be thrown.
     *
     * The feature is disabled by default.
     */
    UNWRAP_SINGLE_VALUE_ARRAYS(false),

    /**
     * Feature to allow "unwrapping" root-level CirJSON value, to match setting of
     * [SerializationFeature.WRAP_ROOT_VALUE] used for serialization. Will verify that the root CirJSON value is a
     * CirJSON Object, and that it has a single property with the expected root name. If not, a [DatabindException] is
     * thrown; otherwise value of the wrapped property will be deserialized as if it was the root value.
     *
     * The feature is disabled by default.
     */
    UNWRAP_ROOT_VALUE(false),

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    /**
     * Feature that can be enabled to allow CirJSON empty String value ("") to be bound as `null` for POJOs and other
     * structured values ([Maps][Map], [Collections][Collection]). If disabled, standard POJOs can only be bound from
     * CirJSON `null` or CirJSON Object (standard meaning that no custom deserializers or constructors are defined; both
     * of which can add support for other kinds of CirJSON values); if enabled, empty CirJSON String can be taken to be
     * equivalent of CirJSON `null`.
     *
     * NOTE: this does NOT apply to scalar values such as booleans, numbers and date/time types; whether these can be
     * coerced depends on [MapperFeature.ALLOW_COERCION_OF_SCALARS].
     *
     * The feature is disabled by default.
     */
    ACCEPT_EMPTY_STRING_AS_NULL_OBJECT(false),

    /**
     * Feature that can be enabled to allow empty CirJSON Array value (that is, `[ "id" ]`) to be bound to POJOs as
     * `null`. If disabled, standard POJOs can only be bound from CirJSON `null` or CirJSON Object (standard meaning
     * that no custom deserializers or constructors are defined; both of which can add support for other kinds of
     * CirJSON values); if enabled, empty JSON Array will be taken to be equivalent of CirJSON `null`.
     *
     * The feature is disabled by default.
     */
    ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT(false),

    /**
     * Feature that determines whether coercion from CirJSON floating point number (anything with command (`.`) or
     * exponent portion (`e` / `E')) to an expected integral number (`Int`, `Long`, `java.math.BigDecimal`) is allowed
     * or not. If enabled, coercion truncates value; if disabled, a [DatabindException] will be thrown.
     *
     * The feature is enabled by default.
     */
    ACCEPT_FLOAT_AS_INT(true),

    /**
     * Feature that determines the deserialization mechanism used for Enum values: if enabled, Enums are assumed to have
     * been serialized using return value of `Enum.toString()`; if disabled, return value of `Enum.name` is assumed to
     * have been used.
     *
     * Note: this feature should usually have the same value as [SerializationFeature.WRITE_ENUMS_USING_TO_STRING].
     *
     * The feature is enabled by default.
     */
    READ_ENUMS_USING_TO_STRING(true),

    /**
     * Feature that allows unknown Enum values to be parsed as `null` values. If disabled, unknown Enum values will
     * throw exceptions.
     *
     * Note that in some cases this will effectively ignore unknown `Enum` values, e.g., when the unknown values are
     * used as keys of [java.util.EnumMap] or values of [java.util.EnumSet]: this is because these data structures
     * cannot store `null` values.
     *
     * Also note that this feature has lower precedence than [READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE], meaning
     * this feature will work only if the latter feature is disabled.
     *
     * The feature is disabled by default.
     */
    READ_UNKNOWN_ENUM_VALUES_AS_NULL(false),

    /**
     * Feature that allows unknown Enum values to be ignored and replaced by a predefined value specified through
     * [@CirJsonEnumDefaultValue][org.cirjson.cirjackson.annotations.CirJsonEnumDefaultValue] annotation. If disabled,
     * unknown Enum values will throw exceptions. If enabled, but no predefined default Enum value is specified, an
     * exception will be thrown as well.
     *
     * Note that this feature has higher precedence than [READ_UNKNOWN_ENUM_VALUES_AS_NULL].
     *
     * The feature is disabled by default.
     */
    READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE(false),

    /**
     * Feature that controls whether numeric timestamp values are expected to be written using nanosecond timestamps
     * (enabled) or not (disabled), **if and only if** datatype supports such resolution. Only newer datatypes (such as
     * Java8 Date/Time) support such resolution -- older types (pre-Java8 `java.util.Date`, etc.) and Joda do not -- and
     * this setting **has no effect** on such types.
     *
     * If disabled, standard millisecond timestamps are assumed. This is the counterpart to
     * [SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS].
     *
     * The feature is enabled by default to support the most accurate time values possible.
     */
    READ_DATE_TIMESTAMPS_AS_NANOSECONDS(true),

    /**
     * Feature that specifies whether context provided [java.util.TimeZone] ([DeserializationContext.timeZone] should be
     * used to adjust Date/Time values on deserialization, even if value itself contains timezone information. If
     * enabled, contextual `TimeZone` will essentially override any other TimeZone information; if disabled, it will
     * only be used if value itself does not contain any TimeZone information.
     *
     * Note that exact behavior depends on date/time types in question; and specifically JDK type of [java.util.Date]
     * does NOT have in-built timezone information so this setting has no effect. Further, while [java.util.Calendar]
     * does have this information basic JDK [java.text.SimpleDateFormat] is unable to retain parsed zone information,
     * and as a result, [java.util.Calendar] will always get context timezone adjustment regardless of this setting.
     *
     * Taking above into account, this feature is supported only by extension modules for Joda and Java 8 date/time
     * datatypes.
     *
     * The feature is enabled by default.
     */
    ADJUST_DATES_TO_CONTEXT_TIME_ZONE(true),

    /*
     *******************************************************************************************************************
     * Other
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whether [ObjectReader] should try to eagerly fetch necessary [ValueDeserializer] when
     * possible. This improves performance in cases where similarly configured [ObjectReader] instance is used multiple
     * times; and should not significantly affect single-use cases.
     *
     * Note that there should not be any need to normally disable this feature: only consider that if there are actual
     * perceived problems.
     *
     * The feature is enabled by default.
     */
    EAGER_DESERIALIZER_FETCH(true);

    override val mask = 1 shl ordinal

    override fun isEnabledIn(flags: Int): Boolean {
        return flags and mask != 0
    }

}