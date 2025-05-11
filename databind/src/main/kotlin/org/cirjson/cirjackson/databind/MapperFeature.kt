package org.cirjson.cirjackson.databind

/**
 * Enumeration that defines simple on/off features to set for [ObjectMapper], and accessible (but not changeable) via
 * [ObjectReader] and [ObjectWriter] (as well as through various convenience methods through context objects).
 */
enum class MapperFeature(val isEnabledByDefault: Boolean) {

    /*
     *******************************************************************************************************************
     * General introspection features
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whether annotation introspection is used for configuration; if enabled, configured
     * [AnnotationIntrospector] will be used: if disabled, no annotations are considered.
     *
     * The feature is enabled by default.
     */
    USE_ANNOTATIONS(true),

    /**
     * Feature that determines whether otherwise regular "getter" methods can be used to get a reference to a Collection
     * and Map to modify the property, without requiring a setter method. However, this only works for getters that
     * handle Collections and Maps, not getters of another type. This is similar to how JAXB framework sets Collections
     * and Maps: no setter is involved, just getter.
     *
     * Note that such getters-as-setters methods have lower precedence than setters, so they are only used if no setter
     * is found for the Map/Collection property.
     *
     * The feature is disabled by default.
     */
    USE_GETTERS_AS_SETTERS(false),

    /**
     * Feature that determines how `transient` modifier for fields is handled: if disabled, it is only taken to mean
     * exclusion of the field as an accessor; if true, it is taken to imply removal of the whole property.
     *
     * The feature is disabled by default, meaning that existence of `transient` for a field does not necessarily lead
     * to ignoral of getters or setters but just ignoring the use of field for access.
     *
     * NOTE! This should have no effect on **explicit** ignoral annotation possibly added to `transient` fields: those
     * should always have expected semantics (same as if field was not `transient`).
     */
    PROPAGATE_TRANSIENT_MARKER(false),

    /**
     * Feature that determines whether getters (getter methods) can be auto-detected if there is no matching mutator
     * (setter, constructor parameter or field) or if there is: if enabled, only getters that match a mutator are
     * auto-discovered; if disabled, all auto-detectable getters can be discovered.
     *
     * The feature is disabled by default.
     */
    REQUIRE_SETTERS_FOR_GETTERS(false),

    /**
     * Feature that determines whether member fields declared as `final` may be auto-detected to be used mutators (used
     * to change the value of the logical property) or not. If enabled, `final` access modifier has no effect, and such
     * fields may be detected according to usual visibility and inference rules; if disabled, such fields are NOT used
     * as mutators except if explicitly annotated for such a use.
     *
     * The feature is disabled by default.
     */
    ALLOW_FINAL_FIELDS_AS_MUTATORS(false),

    /**
     * Feature that determines whether member mutators (fields and setters) may be "pulled in" even if they are not
     * visible, as long as there is a visible accessor (getter or field) with the same name. For example, field "value"
     * may be inferred as mutator if there is visible or explicitly marked getter "getValue()". If enabled, inferring is
     * enabled; otherwise (disabled) only visible and explicitly annotated accessors are ever used.
     *
     * Note that 'getters' are never inferred and need to be either visible (including bean-style naming) or explicitly
     * annotated.
     *
     * The feature is enabled by default.
     */
    INFER_PROPERTY_MUTATORS(true),

    /**
     * Feature that determines handling of `java.beans.ConstructorProperties` annotation: when enabled, it is considered
     * as alias of [org.cirjson.cirjackson.annotations.CirJsonCreator], to mean that constructor should be considered a
     * property-based Creator; when disabled, only constructor parameter name information is used, but constructor is
     * NOT considered an explicit Creator (although te may be discovered as one using other annotations or heuristics).
     *
     * Feature is mostly used to help inter-operability with frameworks like Lombok that may automatically generate
     * `ConstructorProperties` annotation but without necessarily meaning that constructor should be used as Creator for
     * deserialization.
     *
     * The feature is enabled by default.
     */
    INFER_CREATOR_FROM_CONSTRUCTOR_PROPERTIES(true),

    /**
     * Feature that determines whether the nominal property type of [Unit] is allowed for Getter methods to indicate
     * `null` valued pseudo-property or not. If enabled, such properties are recognized; if disabled, such property
     * accessors (or at least getters) are ignored.
     *
     * The feature is enabled by default.
     */
    ALLOW_VOID_VALUED_PROPERTIES(true),

    /*
     *******************************************************************************************************************
     * Access modifier handling
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whether method and field access modifier settings can be overridden when accessing
     * properties. If enabled, method [java.lang.reflect.AccessibleObject.setAccessible] may be called to enable access
     * to otherwise unaccessible objects.
     *
     * Note that this setting may have significant performance implications, since access override helps remove costly
     * access checks on every Reflection access. If you are considering disabling this feature, be sure to verify
     * performance consequences if usage is performance-sensitive. Also note that performance effects vary between Java
     * platforms (Java SE vs. Android, for example), as well as JDK versions: older versions seemed to have more
     * significant performance difference.
     *
     * Conversely, on some platforms, it may be necessary to disable this feature as the platform does not allow such
     * calls. For example, when developing Applets (or other Java code that runs on tightly restricted sandbox), it may
     * be necessary to disable the feature regardless of performance effects.
     *
     * The feature is enabled by default.
     */
    CAN_OVERRIDE_ACCESS_MODIFIERS(true),

    /**
     * Feature that determines that forces call to [java.lang.reflect.AccessibleObject.setAccessible] even for `public`
     * accessors -- that is, even if no such call is needed from functionality perspective -- if call is allowed (that
     * is, [CAN_OVERRIDE_ACCESS_MODIFIERS] is set to `true`). The main reason to enable this feature is possible
     * performance improvement as JDK does not have to perform access checks; these checks are otherwise made for all
     * accessors, including public ones, and may result in slower Reflection calls. Exact impact (if any) depends on
     * Java platform (Java SE, Android) as well as JDK version.
     *
     * The feature is enabled by default.
     */
    OVERRIDE_PUBLIC_ACCESS_MODIFIERS(true),

    /*
     *******************************************************************************************************************
     * Type-handling features
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whether the type detection for serialization should be using actual dynamic runtime type
     * or declared static type. Note that deserialization always uses declared static types since no runtime types are
     * available (as we are creating instances after using type information).
     *
     * This global default value can be overridden at class, method or field level by using
     * [org.cirjson.cirjackson.databind.annotation.CirJsonSerialize.typing] annotation property.
     *
     * The feature is disabled by default, which means that dynamic runtime types are used (instead of declared static
     * types) for serialization.
     */
    USE_STATIC_TYPING(false),

    /**
     * Feature that specifies whether the declared base type of polymorphic value is to be used as the "default"
     * implementation, if no explicit default class is specified via `@CirJsonTypeInfo.defaultImplementation`
     * annotation.
     *
     * Note that feature only affects deserialization of regular polymorphic properties: it does NOT affect
     * non-polymorphic cases, and is unlikely to work with Default Typing.
     *
     * The feature is disabled by default.
     */
    USE_BASE_TYPE_AS_DEFAULT_IMPL(false),

    /**
     * Feature that enables inferring builder type bindings from the value type being deserialized. This requires that
     * the generic type declaration on the value type match that on the builder exactly: mismatched type declarations
     * are not necessarily detected by databind.
     *
     * The feature is enabled by default, which means that deserialization does support deserializing types via builders
     * with type parameters (generic types).
     */
    INFER_BUILDER_TYPE_BINDINGS(true),

    /**
     * Feature that determines what happens when deserializing to a registered subtype (polymorphic deserialization),
     * but no type information has been provided. If enabled, then an `InvalidTypeIdException` will be thrown; if
     * disabled, then the deserialization may proceed without the type information if subtype is legit target
     * (non-abstract).
     *
     * The feature is enabled by default.
     */
    REQUIRE_TYPE_ID_FOR_SUBTYPES(true),

    /*
     *******************************************************************************************************************
     * View-related features
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whether properties that have no view annotations are included in CirJSON serialization
     * views (see [org.cirjson.cirjackson.annotations.CirJsonView] for more details on CirJSON Views). If enabled,
     * non-annotated properties will be included; when disabled, they will be excluded. So this feature changes between
     * "opt-in" (feature disabled) and "opt-out" (feature enabled) modes.
     *
     * The feature is enabled by default, meaning that non-annotated properties are included in all views if there is no
     * [org.cirjson.cirjackson.annotations.CirJsonView] annotation.
     */
    DEFAULT_VIEW_INCLUSION(true),

    /*
     *******************************************************************************************************************
     * Generic output features
     *******************************************************************************************************************
     */

    /**
     * Feature that defines default property serialization order used for POJO properties. If enabled, default ordering
     * is alphabetic (similar to how [org.cirjson.cirjackson.annotations.CirJsonPropertyOrder.alphabetic] works); if
     * disabled, order is unspecified (based on what JDK gives us, which may be declaration order, but is not
     * guaranteed).
     *
     * Note that this is just the default behavior and can be overridden by explicit overrides in classes (for example
     * with [org.cirjson.cirjackson.annotations.CirJsonPropertyOrder] annotation)
     *
     * Note: does **not** apply to [Map] serialization (since entries are not considered Bean/POJO properties.
     *
     * The feature is disabled by default.
     */
    SORT_PROPERTIES_ALPHABETICALLY(false),

    /**
     * Feature that defines whether Creator properties (ones passed through constructor or static factory method) should
     * be sorted before other properties for which no explicit order is specified, in case where alphabetic ordering is
     * to be used for such properties. Note that in either case explicit order (whether by name or by index) will have
     * precedence over this setting.
     *
     * Note: does **not** apply to [Map] serialization (since entries are not considered Bean/POJO properties.
     *
     * WARNING: Disabling it may have a negative impact on deserialization performance. When disabled, all properties
     * before the last creator property in the input need to be buffered, since all creator properties are required to
     * create the instance. Enabling this feature ensures that there is as little buffering as possible.
     *
     * The feature is enabled by default.
     */
    SORT_CREATOR_PROPERTIES_FIRST(true),

    /*
     *******************************************************************************************************************
     * Name-related features
     *******************************************************************************************************************
     */

    /**
     * Feature that will allow for more forgiving deserialization of incoming CirJSON. If enabled, the bean properties
     * will be matched using their lower-case equivalents, meaning that any case-combination (incoming and matching
     * names are canonicalized by lower-casing) should work.
     *
     * Note that there is additional performance overhead since incoming property names need to be lower-cased before
     * comparison, for cases where there are upper-case letters. Overhead for names that are already lower-case should
     * be negligible.
     *
     * The feature is disabled by default.
     */
    ACCEPT_CASE_INSENSITIVE_PROPERTIES(false),

    /**
     * Feature that determines if Enum deserialization should be case-sensitive or not. If enabled, Enum deserialization
     * will ignore the case, that is, case of incoming String value and enum id (depending on other settings, either
     * `name`, `toString()`, or explicit override) do not need to match.
     *
     * This allows both Enum-as-value deserialization and Enum-as-Map-key, unlike some other settings that are separate
     * for value/key handling.
     *
     * The feature is disabled by default.
     */
    ACCEPT_CASE_INSENSITIVE_ENUMS(false),

    /**
     * Feature that permits parsing some enumerated text-based value types but ignoring the case of the values on
     * deserialization: for example, date/time type deserializers. Support for this feature depends on deserializer
     * implementations using it.
     *
     * Note, however, that regular `Enum` types follow [ACCEPT_CASE_INSENSITIVE_ENUMS] setting instead.
     *
     * The feature is disabled by default.
     */
    ACCEPT_CASE_INSENSITIVE_VALUES(false),

    /**
     * Feature that can be enabled to make property names be overridden by wrapper name (usually detected with
     * annotations as defined by [AnnotationIntrospector.findWrapperName]. If enabled, all properties that have
     * associated non-empty Wrapper name will use that wrapper name instead of property name. If disabled, wrapper name
     * is only used for wrapping (if anything).
     *
     * The feature is disabled by default.
     */
    USE_WRAPPER_NAME_AS_PROPERTY_NAME(false),

    /**
     * Feature that when enabled will allow explicitly named properties (i.e., fields or methods annotated with
     * [@CirJsonProperty(value = "explicitName")][org.cirjson.cirjackson.annotations.CirJsonProperty.value]) to be
     * re-named by a [PropertyNamingStrategy], if one is configured.
     *
     * The feature is disabled by default.
     */
    ALLOW_EXPLICIT_PROPERTY_RENAMING(false),

    /**
     * Feature that when enabled will allow getters with is-Prefix also for non-boolean return types; if disabled only
     * methods that return `Boolean` qualify as "is getters".
     *
     * The feature is disabled by default.
     */
    ALLOW_IS_GETTERS_FOR_NON_BOOLEAN(false),

    /*
     *******************************************************************************************************************
     * Coercion features
     *******************************************************************************************************************
     */

    /**
     * Feature that determines whether coercions from secondary representations are allowed for simple non-textual
     * scalar types: numbers and booleans. This includes `primitive` types and their wrappers, but excludes `String` and
     * date/time types.
     *
     * When the feature is disabled, only strictly compatible input may be bound: numbers for numbers, boolean values
     * for booleans. When feature is enabled, conversions from CirJSON String are allowed, as long as textual value
     * matches (for example, String "true" is allowed as equivalent of CirJSON boolean token `true`; or String "1.0" for
     * `Double`).
     *
     * Note that it is possible that other configurability options can override this in closer scope (like on per-type
     * or per-property basis); this is just the global default.
     *
     * The feature is enabled by default.
     */
    ALLOW_COERCION_OF_SCALARS(true),

    /*
     *******************************************************************************************************************
     * Other features
     *******************************************************************************************************************
     */

    /**
     * Setting that determines what happens if an attempt is made to explicitly "merge" value of a property, where value
     * does not support merging; either merging is skipped and a new value is created (`true`) or an exception is
     * thrown (`false`).
     *
     * The feature is enabled by default to allow use of merge defaults even in the presence of some unmergeable
     * properties.
     */
    IGNORE_MERGE_FOR_UNMERGEABLE(true),

    /**
     * Feature that determines whether [ObjectReader] applies default values defined in class definitions in cases where
     * the input data omits the relevant values.
     *
     * Not all modules will respect this feature. Initially, only `cirjackson-module-scala` will respect this feature,
     * but other modules will add support over time.
     *
     * The feature is enabled by default.
     */
    APPLY_DEFAULT_VALUES(true);

    val mask = 1 shl ordinal

    fun isEnabledIn(flags: Int): Boolean {
        return flags and mask != 0
    }

}