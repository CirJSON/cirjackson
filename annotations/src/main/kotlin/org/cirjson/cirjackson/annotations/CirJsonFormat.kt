package org.cirjson.cirjackson.annotations

import org.cirjson.cirjackson.annotations.CirJsonFormat.Companion.DEFAULT_LOCALE
import org.cirjson.cirjackson.annotations.CirJsonFormat.Companion.DEFAULT_TIMEZONE
import org.cirjson.cirjackson.annotations.CirJsonFormat.Feature
import org.cirjson.cirjackson.annotations.CirJsonFormat.Shape
import java.util.*
import kotlin.reflect.KClass

/**
 * General-purpose annotation used for configuring details of how values of properties are to be serialized. Unlike most
 * other CirJackson annotations, annotation does not have specific universal interpretation: instead, effect depends on
 * datatype of property being annotated (or more specifically, deserializer and serializer being used).
 *
 * Common uses include choosing between alternate representations -- for example, whether [java.util.Date] is to be
 * serialized as number (Java timestamp) or String (such as ISO-8601 compatible time value) -- as well as configuring
 * exact details with [pattern] property.
 *
 * Known special handling includes:
 *
 * * [java.util.Date]: Shape can  be [Shape.STRING] or [Shape.NUMBER]; pattern may contain
 * [java.text.SimpleDateFormat]-compatible pattern definition.
 *
 * * Can be used on Classes (types) as well, for modified default behavior, possibly overridden by per-property
 * annotation
 *
 * * [Enums][Enum]: Shapes [Shape.STRING] and [Shape.NUMBER] can be used to change between numeric (index) and textual
 * (name or `toString()`); but it is also possible to use [Shape.OBJECT] to serialize (but not deserialize)
 * [Enums][Enum] as CirJSON Objects (as if they were POJOs). NOTE: serialization as CirJSON Object only works with class
 * annotation; will not work as per-property annotation.
 *
 * * [Collections][Collection] can be serialized as (and deserialized from) JSON Objects, if [Shape.OBJECT] is used.
 * NOTE: can ONLY be used as class annotation; will not work as per-property annotation.
 *
 * * [Number] subclasses can be serialized as full objects if [Shape.OBJECT] is used. Otherwise, the default behavior of
 * serializing to a scalar number value will be preferred. NOTE: can ONLY be used as class annotation; will not work as
 * per-property annotation.
 *
 * @property pattern Datatype-specific additional piece of configuration that may be used to further refine formatting
 * aspects. This may, for example, determine low-level format String used for [java.util.Date] serialization; however,
 * exact use is determined by specific `CirJsonSerializer`
 *
 * @property shape Structure to use for serialization: definition of mapping depends on datatype, but usually has
 * straight-forward counterpart in data format (CirJSON). Note that commonly only a subset of shapes is available; and
 * if 'invalid' value is chosen, defaults are usually used.
 *
 * @property locale [Locale] to use for serialization (if needed). Special value of [DEFAULT_LOCALE] can be used to mean
 * "just use the default", where default is specified by the serialization context, which in turn defaults to system
 * defaults ([Locale.getDefault]) unless explicitly set to another locale.
 *
 * @property timezone [TimeZone] to use for serialization (if needed). Special value of [DEFAULT_TIMEZONE] can be used
 * to mean "just use the default", where default is specified by the serialization context, which in turn defaults to
 * system default (UTC) unless explicitly set to another timezone.
 *
 * @property lenient Property that indicates whether "lenient" handling should be enabled or disabled. This is relevant
 * mostly for deserialization of some textual datatypes, especially date/time types.
 *
 * Note that underlying default setting depends on datatype (or more precisely deserializer for it): for most date/time
 * types, default is for leniency to be enabled.
 *
 * @property with Set of [Features][Feature] to explicitly enable with respect to the handling of annotated property.
 * This will have precedence over possible global configuration.
 *
 * @property without Set of [Features][Feature] to explicitly disable with respect to the handling of annotated
 * property. This will have precedence over possible global configuration.
 *
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonFormat(val pattern: String = "", val shape: Shape = Shape.ANY,
        val locale: String = DEFAULT_LOCALE, val timezone: String = DEFAULT_TIMEZONE,
        val lenient: OptionalBoolean = OptionalBoolean.DEFAULT, val with: Array<Feature> = emptyArray(),
        val without: Array<Feature> = emptyArray()) {

    /**
     * Value enumeration used for indicating preferred Shape; translates loosely to CirJSON types, with some extra
     * values to indicate less precise choices (i.e. allowing one of multiple actual shapes)
     */
    enum class Shape {

        // // // Concrete physical shapes, scalars

        /**
         * Value that indicates that Binary type (native, if format supports it; encoding using Base64 if only textual
         * types supported) should be used.
         */
        BINARY,

        /**
         * Value that indicates that (CirJSON) boolean type (`true`, `false`) should be used.
         */
        BOOLEAN,

        /**
         * Value that indicates that a numeric (CirJSON) type should be used (but does not specify whether integer or
         * floating-point representation should be used)
         */
        NUMBER,

        /**
         * Value that indicates that floating-point numeric type should be used (and not [NUMBER_INT]).
         */
        NUMBER_FLOAT,

        /**
         * Value that indicates that integer number type should be used (and not [NUMBER_FLOAT]).
         */
        NUMBER_INT,

        /**
         * Value that indicates that (CirJSON) String type should be used.
         */
        STRING,

        /**
         * Value that indicates shape should not be structural (that is, not [ARRAY] or [OBJECT]), but can be any other
         * shape.
         */
        SCALAR,

        // // // Concrete physical shapes, structured

        /**
         * Value that indicates that (CirJSON) Array type should be used.
         */
        ARRAY,

        /**
         * Value that indicates that (CirJSON) Object type should be used.
         */
        OBJECT,

        // // // Additional logical meta-types

        /**
         * Marker enum value that indicates "whatever" choice, meaning that annotation does NOT specify shape to use.
         * Note that this is different from [NATURAL], which specifically instructs use of the "natural" shape for
         * datatype.
         */
        ANY,

        /**
         * Marker enum value that indicates the "default" choice for given datatype; for example, CirJSON String for
         * [String], or CirJSON Number for numbers. Note that this is different from [ANY] in that this is actual
         * explicit choice that overrides possible default settings.
         */
        NATURAL,

        /**
         * Marker enum value that indicates not only shape of [OBJECT] but further handling as POJO, where applicable.
         * Mostly makes difference at Java Object level when distinguishing handling between [Map] and POJO types.
         */
        POJO;

        val isNumeric: Boolean
            get() = this == NUMBER || this == NUMBER_FLOAT || this == NUMBER_INT

        val isStructured: Boolean
            get() = this == OBJECT || this == ARRAY || this == POJO

    }

    /**
     * Set of features that can be enabled/disabled for property annotated. These often relate to specific
     * `SerializationFeature` or `DeserializationFeature`, as noted by entries.
     *
     * Note that whether specific setting has an effect depends on whether `CirJsonSerializer` / `CirJsonDeserializer`
     * being used takes the format setting into account. If not, please file an issue for adding support via issue
     * tracker for package that has handlers (if you know which one; if not, just use `cirjackson-databind`).
     */
    enum class Feature {

        /**
         * Override for `DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY` which will allow deserialization of
         * CirJSON non-array values into single-element Java arrays and [Collections][Collection].
         */
        ACCEPT_SINGLE_VALUE_AS_ARRAY,

        /**
         * Override for `MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES`, which allows case-insensitive matching of
         * property names (but NOT values, see [ACCEPT_CASE_INSENSITIVE_VALUES] for that).
         *
         * Only affects deserialization, has no effect on serialization.
         */
        ACCEPT_CASE_INSENSITIVE_PROPERTIES,

        /**
         * Override for `DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL`, which allows unknown Enum values to
         * be parsed as null values.
         */
        READ_UNKNOWN_ENUM_VALUES_AS_NULL,

        /**
         * Override for `DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE`, which allows unknown Enum
         * values to be ignored and a predefined value specified through
         * [@CirJsonEnumDefaultValue][CirJsonEnumDefaultValue] annotation.
         */
        READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE,

        /**
         * Override for `DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE`, (counterpart to
         * [WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS]), similar constraints apply.
         */
        READ_DATE_TIMESTAMPS_AS_NANOSECONDS,

        /**
         * Override for `MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES`, which allows case-sensitive matching of (some)
         * property values, such as `Enums`. Only affects deserialization, has no effect on serialization.
         */
        ACCEPT_CASE_INSENSITIVE_VALUES,

        /**
         * Override for `SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS`, similar constraints apply.
         */
        WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS,

        /**
         * Override for `SerializationFeature.WRITE_DATES_WITH_ZONE_ID`, similar constraints apply.
         */
        WRITE_DATES_WITH_ZONE_ID,

        /**
         * Override for `SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED` which will force serialization of
         * single-element arrays and [Collections][Collection] as that single element and excluding array wrapper.
         */
        WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED,

        /**
         * Override for `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS`, enabling of which will force sorting of [Map]
         * keys before serialization.
         */
        WRITE_SORTED_MAP_ENTRIES,

        /**
         * Override for `DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIMEZONE` that specifies whether context
         * provided timezone `DeserializationContext.getTimeZone()` should be used to adjust Date/Time values on
         * deserialization, even if value itself contains timezone information
         *
         * NOTE: due to limitations of "old" JDK date/time types (that is, [java.util.Date] and [java.util.Calendar]),
         * this setting is only applicable to `Joda` and `Java 8 date/time` values, but not to `java.util.Date` or
         * `java.util.Calendar`.
         */
        ADJUST_DATES_TO_CONTEXT_TIME_ZONE

    }

    class Features private constructor(private val myEnabled: Int, private val myDisabled: Int) {

        fun withOverrides(overrides: Features?): Features {
            overrides ?: return this

            val overrideDisabled = overrides.myDisabled
            val overrideEnabled = overrides.myEnabled

            if (overrideDisabled == 0 && overrideEnabled == 0) {
                return this
            }

            if (myDisabled == 0 && myEnabled == 0) {
                return this
            }

            val enabled = myEnabled and overrideDisabled.inv() or overrideEnabled
            val disabled = myDisabled and overrideEnabled.inv() or overrideDisabled

            if (enabled == myEnabled && disabled == myDisabled) {
                return this
            }

            return Features(enabled, disabled)
        }

        fun with(vararg features: Feature): Features {
            var e = 0

            for (feature in features) {
                e = 1 shl feature.ordinal or e
            }

            if (e == myEnabled) {
                return this
            }

            return Features(e, myDisabled)
        }

        fun without(vararg features: Feature): Features {
            var d = 0

            for (feature in features) {
                d = 1 shl feature.ordinal or d
            }

            if (d == myDisabled) {
                return this
            }

            return Features(myEnabled, d)
        }

        fun get(feature: Feature): Boolean? {
            val mask = 1 shl feature.ordinal

            if (myDisabled and mask != 0) {
                return false
            }

            if (myEnabled and mask != 0) {
                return true
            }

            return null
        }

        override fun toString(): String {
            if (this == EMPTY) {
                return "EMPTY"
            }

            return "(enabled=0x${myEnabled.toString(16)},disabled=0x${myDisabled.toString(16)})"
        }

        override fun hashCode(): Int {
            return myEnabled + myDisabled
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }

            if (javaClass != other?.javaClass) {
                return false
            }

            other as Features

            return myEnabled == other.myEnabled && myDisabled == other.myDisabled
        }

        companion object {

            val EMPTY = Features(0, 0)

            fun construct(format: CirJsonFormat): Features {
                return construct(format.with, format.without)
            }

            fun construct(enabled: Array<Feature>, disabled: Array<Feature>): Features {
                var e = 0

                for (feature in enabled) {
                    e = 1 shl feature.ordinal or e
                }

                var d = 0

                for (feature in disabled) {
                    d = 1 shl feature.ordinal or d
                }

                return Features(e, d)
            }

        }

    }

    class Value : CirJacksonAnnotationValue<CirJsonFormat> {

        val pattern: String

        val shape: Shape

        val locale: Locale?

        val myTimezoneString: String?

        /**
         * `true` if explicitly set to `true`; `false` if explicit set to `false`; or `null` if not set either way
         * (assuming "default leniency" for the context)
         */
        val lenient: Boolean?

        /**
         * Full set of features enabled/disabled.
         */
        val features: Features

        private var myTimezone: TimeZone?

        constructor(pattern: String?, shape: Shape?, locale: Locale?, timezoneString: String?, timezone: TimeZone?,
                features: Features?, lenient: Boolean?) {
            this.pattern = pattern ?: ""
            this.shape = shape ?: Shape.ANY
            this.locale = locale
            myTimezoneString = timezoneString
            myTimezone = timezone
            this.features = features ?: Features.EMPTY
            this.lenient = lenient
        }

        constructor(pattern: String?, shape: Shape?, locale: Locale?, timezone: TimeZone?, features: Features?,
                lenient: Boolean?) {
            this.pattern = pattern ?: ""
            this.shape = shape ?: Shape.ANY
            this.locale = locale
            myTimezoneString = null
            myTimezone = timezone
            this.features = features ?: Features.EMPTY
            this.lenient = lenient
        }

        constructor(pattern: String?, shape: Shape?, localeString: String?, timezoneString: String?,
                features: Features?, lenient: Boolean?) : this(pattern, shape,
                if (localeString.isNullOrEmpty() || DEFAULT_LOCALE == localeString) null else Locale(localeString),
                if (timezoneString.isNullOrEmpty() || DEFAULT_TIMEZONE == timezoneString) null else timezoneString,
                null, features, lenient)

        constructor() : this("", Shape.ANY, "", "", Features.EMPTY, null)

        constructor(annotation: CirJsonFormat) : this(annotation.pattern, annotation.shape, annotation.locale,
                annotation.timezone, Features.construct(annotation), annotation.lenient.asBoolean())

        fun withOverrides(overrides: Value?): Value {
            if (overrides == null || overrides === EMPTY || overrides === this) {
                return this
            }

            if (this === EMPTY) {
                return overrides
            }

            val pattern = overrides.pattern.takeUnless { it.isEmpty() } ?: pattern
            val shape = overrides.shape.takeUnless { it == Shape.ANY } ?: shape
            val locale = overrides.locale ?: locale
            val features = features.withOverrides(overrides.features)
            val lenient = overrides.lenient ?: lenient
            val (timezoneString, timezone) = (overrides.myTimezoneString to overrides.myTimezone).takeUnless { it.first.isNullOrEmpty() }
                    ?: (myTimezoneString to myTimezone)

            return Value(pattern, shape, locale, timezoneString, timezone, features, lenient)
        }

        fun withPattern(pattern: String?): Value {
            if (pattern == this.pattern) {
                return this
            }

            return Value(pattern, shape, locale, myTimezoneString, myTimezone, features, lenient)
        }

        fun withShape(shape: Shape?): Value {
            if (shape == this.shape) {
                return this
            }

            return Value(pattern, shape, locale, myTimezoneString, myTimezone, features, lenient)
        }

        fun withLocale(locale: Locale?): Value {
            if (locale == this.locale) {
                return this
            }

            return Value(pattern, shape, locale, myTimezoneString, myTimezone, features, lenient)
        }

        fun withTimeZone(timezone: TimeZone): Value {
            if (timezone == myTimezone) {
                return this
            }

            return Value(pattern, shape, locale, null, myTimezone, features, lenient)
        }

        fun withLenient(lenient: Boolean?): Value {
            if (lenient == this.lenient) {
                return this
            }

            return Value(pattern, shape, locale, myTimezoneString, myTimezone, features, lenient)
        }

        fun withFeature(feature: Feature): Value {
            val features = features.with(feature)

            if (features === this.features) {
                return this
            }

            return Value(pattern, shape, locale, myTimezoneString, myTimezone, features, lenient)
        }

        fun withoutFeature(feature: Feature): Value {
            val features = features.without(feature)

            if (features === this.features) {
                return this
            }

            return Value(pattern, shape, locale, myTimezoneString, myTimezone, features, lenient)
        }

        override fun valueFor(): KClass<CirJsonFormat> {
            return CirJsonFormat::class
        }

        /**
         * Convenience method equivalent to
         * ```
         *   lenient == true
         * ```
         * that is, returns `true` if (and only if) leniency has been explicitly set to `true`; but not if it is
         * undefined.
         */
        val isLenient: Boolean
            get() = lenient == true

        /**
         * Alternate access (compared to [timeZone]) which is useful when caller just wants time zone id to convert, but
         * not as JDK provided [TimeZone]
         */
        fun timeZoneAsString(): String? {
            return myTimezone?.id ?: myTimezoneString
        }

        val timeZone: TimeZone?
            get() {
                if (myTimezone == null) {
                    myTimezoneString ?: return null
                    myTimezone = TimeZone.getTimeZone(myTimezoneString)
                }

                return myTimezone
            }

        fun hasShape(): Boolean {
            return shape != Shape.ANY
        }

        fun hasPattern(): Boolean {
            return pattern.isNotEmpty()
        }

        fun hasLocale(): Boolean {
            return locale != null
        }

        fun hasTimezone(): Boolean {
            return myTimezone != null || !myTimezoneString.isNullOrEmpty()
        }

        /**
         * Accessor for checking whether there is a setting for leniency. NOTE: does NOT mean that `lenient` is `true`
         * necessarily; just that it has been set.
         */
        fun hasLenient(): Boolean {
            return lenient != null
        }

        /**
         * Accessor for checking whether this format value has specific setting for given feature. Result is 3-valued
         * with either `null`, `true`, or `false`, indicating 'yes/no/dunno' choices, where `null` ("dunno") indicates
         * that the default handling should be used based on global defaults, and there is no format override.
         */
        fun getFeature(feature: Feature): Boolean? {
            return features.get(feature)
        }

        override fun toString(): String {
            return "CirJsonFormat.Value(pattern=$pattern,shape=$shape,lenient=$lenient,locale=$locale,timezone=$myTimezoneString,features=$features)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }

            if (javaClass != other?.javaClass) {
                return false
            }

            other as Value

            if (shape != other.shape || features != other.features) {
                return false
            }

            return lenient == other.lenient && myTimezoneString == other.myTimezoneString && pattern == other.pattern &&
                    myTimezone == other.myTimezone && locale == other.locale
        }

        override fun hashCode(): Int {
            var hash = myTimezoneString?.hashCode() ?: 1
            hash = hash xor pattern.hashCode()
            hash += shape.hashCode()

            if (lenient != null) {
                hash = hash xor lenient.hashCode()
            }

            if (locale != null) {
                hash += locale.hashCode()
            }

            hash = hash xor features.hashCode()
            return hash
        }

        companion object {

            val EMPTY = Value()

            /**
             * Helper method that will try to combine values from two [Value] instances, using one as base settings, and
             * the other as overrides to use instead of base values when defined; base values are only use if override
             * does not specify a value (matching value is null or logically missing). Note that one or both of value
             * instances may be `null`, directly; if both are `null`, result will also be `null`; otherwise, never
             * `null`.
             */
            fun merge(base: Value?, overrides: Value?): Value? {
                return base?.withOverrides(overrides) ?: overrides
            }

            fun mergeAll(vararg values: Value?): Value? {
                var result: Value? = null

                for (value in values) {
                    result = merge(result, value)
                }

                return result
            }

            fun from(annotation: CirJsonFormat?): Value {
                return if (annotation != null) Value(annotation) else EMPTY
            }

            fun forPattern(pattern: String?): Value {
                return Value(pattern, null, null, null, null, Features.EMPTY, null)
            }

            fun forShape(shape: Shape?): Value {
                return Value("", shape, null, null, null, Features.EMPTY, null)
            }

            fun forLeniency(lenient: Boolean?): Value {
                return Value("", null, null, null, null, Features.EMPTY, lenient)
            }

        }

    }

    companion object {

        /**
         * Value that indicates that default [Locale] (from deserialization or serialization context) should be used:
         * annotation does not define value to use.
         */
        const val DEFAULT_LOCALE = "##default"

        /**
         * Value that indicates that default [TimeZone] (from deserialization or serialization context) should be used:
         * annotation does not define value to use.
         *
         * NOTE: default here does NOT mean JVM defaults but CirJackson databindings default, usually UTC, but may be
         * changed on `ObjectMapper`.
         */
        const val DEFAULT_TIMEZONE = "##default"

    }

}