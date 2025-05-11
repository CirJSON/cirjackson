package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.databind.configuration.ConfigFeature

/**
 * Enumeration that defines simple on/off features that affect the way objects are serialized.
 *
 * Note that features can be set both through [ObjectMapper] (as sort of defaults) and through [ObjectWriter]. In the
 * first case, these defaults must follow "config-then-use" patterns (i.e., defined once, not changed afterward); all
 * per-call changes must be done using [ObjectWriter].
 */
enum class SerializationFeature(override val isEnabledByDefault: Boolean) : ConfigFeature {

    /*
     *******************************************************************************************************************
     * Generic output features
     *******************************************************************************************************************
     */

    /**
     * Feature that can be enabled to make root value (usually CirJSON Object but can be any type) wrapped within a
     * single property CirJSON object, where key as the "root name", as determined by annotation introspector (esp. for
     * JAXB that uses `@XmlRootElement.name`) or fallback (nonqualified class name). The feature is mostly intended for
     * JAXB compatibility.
     *
     * The feature is disabled by default.
     */
    WRAP_ROOT_VALUE(false),

    /**
     * Feature that allows enabling (or disabling) indentation for the underlying generator, using the default pretty
     * printer configured for [ObjectMapper] (and [ObjectWriters][ObjectWriter] created from mapper).
     *
     * Note that the default pretty printer is only used if no explicit [org.cirjson.cirjackson.core.PrettyPrinter] has
     * been configured for the generator or [ObjectWriter].
     *
     * The feature is disabled by default.
     */
    INDENT_OUTPUT(false),

    /*
     *******************************************************************************************************************
     * Error handling features
     *******************************************************************************************************************
     */

    /**
     * Feature that determines what happens when no accessors are found for a type (and there are no annotations to
     * indicate it is meant to be serialized). If enabled (default), an exception is thrown to indicate these as
     * non-serializable types; if disabled, they are serialized as empty Objects, i.e., without any properties.
     *
     * Note that empty types that this feature has only effect on those "empty" beans that do not have any recognized
     * annotations (like `@CirJsonSerialize`): ones that do have annotations do not result in an exception being thrown.
     *
     * The feature is enabled by default.
     */
    FAIL_ON_EMPTY_BEANS(true),

    /**
     * Feature that determines what happens when a direct self-reference is detected by a POJO (and no Object ID
     * handling is enabled for it): either a [DatabindException] is thrown (if `true`), or reference is normally
     * processed (`false`).
     *
     * The feature is enabled by default.
     */
    FAIL_ON_SELF_REFERENCES(true),

    /**
     * Feature that determines whether CirJackson code should catch and wrap [Exceptions][Exception] (but never
     * [Errors][Error]!) to add additional information about location (within input) of the problem or not. If enabled,
     * most exceptions will be caught and re-thrown (exception specifically being that
     * [IOExceptions][java.io.IOException] may be passed as is, since they are declared as throwable); this can be
     * convenient both in that all exceptions will be checked and declared, and so there is more contextual information.
     * However, sometimes calling application may just want "raw" unchecked exceptions passed as is.
     *
     * The feature is enabled by default.
     */
    WRAP_EXCEPTIONS(true),

    /**
     * Feature that determines what happens when an object which normally has type information included by CirJackson is
     * used in conjunction with [org.cirjson.cirjackson.annotations.CirJsonUnwrapped]. In the default (enabled) state,
     * an error will be thrown when an unwrapped object has type information. When disabled, the object will be
     * unwrapped and the type information discarded.
     *
     * The feature is enabled by default.
     */
    FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS(true),

    /**
     * Feature that determines what happens when a direct self-reference is detected by a POJO (and no Object ID
     * handling is enabled for it): if enabled, write that reference as `null`; if disabled, default behavior is used
     * (which will try to serialize usually resulting in exception). But if
     * [SerializationFeature.FAIL_ON_SELF_REFERENCES] is enabled, this property is ignored.
     *
     * The feature is disabled by default.
     */
    WRITE_SELF_REFERENCES_AS_NULL(false),

    /*
     *******************************************************************************************************************
     * Output life cycle features
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whether `close` method of serialized **root level** objects (ones for which
     * `ObjectMapper`'s `writeValue()` (or equivalent) method is called) that implement [java.io.Closeable] is called
     * after serialization or not. If enabled, **`close()`** will be called after serialization completes (whether
     * successfully, or due to an error manifested by an exception being thrown). You can think of this as sort of
     * "finally" processing.
     *
     * NOTE: it only affects behavior with **root** objects, and not other objects reachable from the root object. Put
     * another way, only one call will be made for each `writeValue` call.
     *
     * The feature is disabled by default.
     */
    CLOSE_CLOSEABLE(false),

    /**
     * Feature that determines whether `CirJsonGenerator.flush()` is called after `writeValue()` method **that takes
     * `CirJsonGenerator` as an argument** completes (i.e., does NOT affect methods that use other destinations); same
     * for methods in [ObjectWriter]. This usually makes sense; but there are cases where flushing should not be forced:
     * for example, when the underlying stream is compressing and `flush()` causes compression state to be flushed
     * (which occurs with some compression codecs).
     *
     * The feature is enabled by default.
     */
    FLUSH_AFTER_WRITE_VALUE(true),

    /*
     *******************************************************************************************************************
     * Datatype-specific serialization configuration
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whether Date (and date/time) values (and Date-based things like
     * [Calendars][java.util.Calendar]) are to be serialized as numeric timestamps (`true`; the default), or as
     * something else (usually textual representation). If textual representation is used, the actual format depends on
     * configuration settings including possible per-property use of `@CirJsonFormat` annotation, globally configured
     * [java.text.DateFormat].
     *
     * For "classic" JDK date types ([java.util.Date], [java.util.Calendar]) the default formatting is provided by
     * [org.cirjson.cirjackson.databind.util.StandardDateFormat], and corresponds to format String of
     * `yyyy-MM-dd'T'HH:mm:ss.SSSX` (see [java.text.DateFormat] for details of format Strings). Whether this feature
     * affects handling of other date-related types depends on handlers of those types, although ideally they should use
     * this feature
     *
     * Note: whether [Map] keys are serialized as Strings or not is controlled using [WRITE_DATE_KEYS_AS_TIMESTAMPS]
     * instead of this feature.
     *
     * The feature is enabled by default, so that date/time are by default serialized as timestamps.
     */
    WRITE_DATES_AS_TIMESTAMPS(true),

    /**
     * Feature that determines whether [Dates][java.util.Date] (and subtypes) used as [Map] keys are serialized as
     * timestamps or not (if not, will be serialized as textual values).
     *
     * Default value is `false`, meaning that Date-valued Map keys are serialized as textual (ISO-8601) values.
     *
     * The feature is disabled by default.
     */
    WRITE_DATE_KEYS_AS_TIMESTAMPS(false),

    /**
     * Feature that determines whether date/date-time values should be serialized so that they include timezone id, in
     * cases where the type itself contains timezone information. Including this information may lead to compatibility
     * issues because the ISO-8601 specification does not define formats that include such information.
     *
     * If enabled, Timezone id should be included using format specified with Java 8
     * `DateTimeFormatter.ISO_ZONED_DATE_TIME` definition (for example, `2011-12-03T10:15:30+01:00[Europe/Paris]`).
     *
     * Note: setting has no relevance if date/time values are serialized as timestamps.
     *
     * The feature is disabled by default, so that zone id is NOT included; rather, timezone offset is used for ISO-8601
     * compatibility (if any timezone information is included in value).
     */
    WRITE_DATES_WITH_ZONE_ID(false),

    /**
     * Feature that determines whether timezone/offset included in zoned date/time values (note: does NOT
     * [java.util.Date] will be overridden if there is an explicitly set context time zone. If disabled, timezone/offset
     * value is used-is; if enabled, context time zone is used instead.
     *
     * Note that this setting only affects "Zoned" date/time values of `Java 8 date/time` types -- it will have no
     * effect on old [java.util] value handling (of which [java.util.Date] has no timezone information and must use
     * contextual timezone, implicit or explicit; and [java.util.Calendar] which will always use timezone Calendar value
     * has). The setting is also ignored by Joda date/time values.
     *
     * The feature is enabled by default.
     */
    WRITE_DATES_WITH_CONTEXT_TIME_ZONE(true),

    /**
     * Feature that determines whether time values that represent time periods (durations, periods, ranges) are to be
     * serialized by default using a numeric (`true`) or textual (`false`) representations. Note that numeric
     * representation may mean either a simple number or an array of numbers, depending on type.
     *
     * Note: whether [Map] keys are serialized as Strings or not is controlled using [WRITE_DATE_KEYS_AS_TIMESTAMPS].
     *
     * The feature is enabled by default, so that period/duration are by default serialized as timestamps.
     */
    WRITE_DURATIONS_AS_TIMESTAMPS(true),

    /**
     * Feature that determines how type `CharArray` is serialized: when enabled, will be serialized as an explicit
     * CirJSON array (with single-character Strings as values); when disabled, defaults to serializing them as Strings
     * (which is more compact).
     *
     * The feature is disabled by default.
     */
    WRITE_CHAR_ARRAYS_AS_CIRJSON_ARRAYS(false),

    /**
     * Feature that determines standard serialization mechanism used for Enum values: if enabled, return value of
     * `Enum.toString()` is used; if disabled, return value of `Enum.name` is used.
     *
     * Note: this feature should usually have the same value as [DeserializationFeature.READ_ENUMS_USING_TO_STRING].
     *
     * The feature is enabled by default.
     */
    WRITE_ENUMS_USING_TO_STRING(true),

    /**
     * Feature that determines whether Enum values are serialized as numbers (`true`), or textual values (`false`). If
     * textual values are used, other settings are also considered. If this feature is enabled, return value of
     * `Enum.ordinal` (an Int) will be used as the serialization.
     *
     * Note that this feature has precedence over [WRITE_ENUMS_USING_TO_STRING], which is only considered if this
     * feature is set to `false`.
     *
     * Note that this does NOT apply to [Enums][Enum] written as keys of [Map] values, which has separate setting,
     * [WRITE_ENUM_KEYS_USING_INDEX].
     *
     * The feature is disabled by default.
     */
    WRITE_ENUMS_USING_INDEX(false),

    /**
     * Feature that determines whether [Enums][Enum] used as [Map] keys are serialized as using [Enum.ordinal] or not.
     * Similar to [WRITE_ENUMS_USING_INDEX] used when writing [Enums][Enum] as regular values.
     *
     * NOTE: the counterpart for this setting is
     * [org.cirjson.cirjackson.databind.configuration.EnumFeature.READ_ENUM_KEYS_USING_INDEX].
     *
     * The feature is disabled by default.
     */
    WRITE_ENUM_KEYS_USING_INDEX(false),

    /**
     * Feature added for interoperability, to work with oddities of the so-called "BadgerFish" convention. Feature
     * determines handling of the single element [Collections][Collection] and arrays: if enabled,
     * [Collections][Collection] and arrays that contain exactly one element will be serialized as if that element
     * itself was serialized.
     *
     * When enabled, a POJO with array that normally looks like this:
     * ```
     *  { "arrayProperty" : [ 1 ] }
     * ```
     * will instead be serialized as
     * ```
     *  { "arrayProperty" : 1 }
     * ```
     *
     * Note that this feature is counterpart to [DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY] (that is, usually
     * both are enabled, or neither is).
     *
     * The feature is disabled by default, so that no special handling is done.
     */
    WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED(false),

    /**
     * Feature that controls whether numeric timestamp values are to be written using nanosecond timestamps (enabled) or
     * not (disabled); **if and only if** datatype supports such resolution. Only newer datatypes (such as Java 8
     * Date/Time) support such resolution -- older types (pre-Java8 **`java.util.Date`** etc.) and Joda do not -- and
     * this setting **has no effect** on such types.
     *
     * If disabled, standard millisecond timestamps are assumed. This is the counterpart to
     * [DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS].
     *
     * The feature is enabled by default to support the most accurate time values possible.
     */
    WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS(true),

    /**
     * Feature that determines whether [Map] entries are first sorted by key before serialization or not: if enabled, an
     * additional sorting step is performed if necessary (not necessary for [SortedMaps][java.util.SortedMap]), if
     * disabled, no additional sorting is needed.
     *
     * The feature is disabled by default.
     */
    ORDER_MAP_ENTRIES_BY_KEYS(false),

    /*
     *******************************************************************************************************************
     * Other
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whether [ObjectWriter] should try to eagerly fetch necessary [ValueSerializer] when
     * possible. This improves performance in cases where similarly configured [ObjectWriter] instance is used multiple
     * times; and should not significantly affect single-use cases.
     *
     * Note that there should not be any need to normally disable this feature: only consider that if there are actual
     * perceived problems.
     *
     * The feature is enabled by default.
     */
    EAGER_SERIALIZER_FETCH(true),

    /**
     * Feature that determines whether Object Identity is compared using true JVM-level identity of Object (`false`);
     * or, `equals()` method. The latter is sometimes useful when dealing with Database-bound objects with ORM libraries
     * (like Hibernate). Note that the Object itself is actually compared, and NOT Object ID; naming of this feature is
     * somewhat confusing, so it is important that the Objects **for which identity is to be preserved** are considered
     * equal, above and beyond ids (which are always compared using equality anyway).
     *
     * NOTE: due to the way functionality is implemented, it is very important that in addition to overriding
     * [Any.equals] for Objects to match (to be considered "same"), it is also necessary to ensure that [Any.hashCode]
     * is overridden to produce the exact same value for equal instances.
     *
     * The feature is disabled by default, meaning that strict identity is used, not `equals()`.
     */
    USE_EQUALITY_FOR_OBJECT_ID(false);

    override val mask = 1 shl ordinal

    override fun isEnabledIn(flags: Int): Boolean {
        return flags and mask != 0
    }

}