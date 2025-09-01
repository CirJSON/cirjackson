package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.*
import org.cirjson.cirjackson.core.Versioned
import org.cirjson.cirjackson.databind.annotation.CirJsonSerialize
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.util.NameTransformer
import kotlin.reflect.KClass

/**
 * Abstract class that defines API used for introspecting annotation-based configuration for serialization and
 * deserialization. Separated so that different sets of annotations can be supported, and support plugged-in
 * dynamically.
 *
 * Although default implementations are based on using annotations as the only (or at least main) information source,
 * custom implementations are not limited in such a way, and in fact there is no expectation they should be. So the name
 * is a bit of a misnomer; this is a general configuration introspection facility.
 *
 * NOTE: due to rapid addition of new methods (and changes to existing methods), it is **strongly** recommended that
 * custom implementations should not directly extend this class, but rather extend [NopAnnotationIntrospector]. This
 * way, added methods will not break backwards compatibility of custom annotation introspectors.
 */
abstract class AnnotationIntrospector : Versioned {

    /*
     *******************************************************************************************************************
     * Access to possibly chained introspectors
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to collect all "real" introspectors that this introspector contains, if any; or this
     * introspector if it is not a container. Used to get access to all container introspectors in their priority order.
     *
     * Default implementation returns a Singleton list with this introspector as contents. This usually works for
     * subclasses, except for proxy or delegating "container introspectors" which need to override implementation.
     *
     * @return Collection of all introspectors starting with this one, in case multiple introspectors are chained
     */
    open fun allIntrospectors(): Collection<AnnotationIntrospector> {
        return listOf(this)
    }

    /**
     * Method that can be used to collect all "real" introspectors that this introspector contains, if any; or this
     * introspector if it is not a container. Used to get access to all container introspectors in their priority order.
     *
     * Default implementation adds this introspector in result; this usually works for subclasses, except for proxy or
     * delegating "container introspectors" which need to override implementation.
     *
     * @param result Container to add introspectors to
     *
     * @return Passed in `Collection` filled with introspectors as explained above
     */
    open fun allIntrospectors(result: MutableCollection<AnnotationIntrospector>): Collection<AnnotationIntrospector> {
        return result.apply { add(this@AnnotationIntrospector) }
    }

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    /**
     * Method for checking whether given annotation is considered an annotation bundle: if so, all meta-annotations it
     * has will be used instead of annotation ("bundle") itself.
     *
     * @param annotation Annotated entity to introspect
     *
     * @return `true` if given annotation is considered an annotation bundle; `false` if not
     */
    open fun isAnnotationBundle(annotation: Annotation): Boolean {
        return false
    }

    /*
     *******************************************************************************************************************
     * Annotations for Object ID handling
     *******************************************************************************************************************
     */

    /**
     * Method for checking whether given annotated thing (type, or accessor) indicates that values referenced (values of
     * type of annotated class, or values referenced by annotated property; latter having precedence) should include
     * Object Identifier, and if so, specify details of Object Identity used.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotated Annotated entity to introspect
     *
     * @return Details of Object ID as explained above, if Object ID handling to be applied; `null` otherwise.
     */
    open fun findObjectIdInfo(config: MapperConfig<*>, annotated: Annotated): ObjectIdInfo? {
        return null
    }

    /**
     * Method for figuring out additional properties of an Object Identity reference
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotated Annotated entity to introspect
     *
     * @param objectIdInfo (optional) Base Object ID information, if any; `null` if none
     *
     * @return [ObjectIdInfo] augmented with possible additional information
     */
    open fun findObjectReferenceInfo(config: MapperConfig<*>, annotated: Annotated,
            objectIdInfo: ObjectIdInfo?): ObjectIdInfo? {
        return objectIdInfo
    }

    /*
     *******************************************************************************************************************
     * General class annotations
     *******************************************************************************************************************
     */

    /**
     * Method for locating name used as "root name" (for use by some serializers when outputting root-level object --
     * mostly for XML compatibility purposes) for given class, if one is defined. Returns `null` if no declaration
     * found; can return explicit empty String, which is usually ignored as well as `null`.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     */
    open fun findRootName(config: MapperConfig<*>, annotatedClass: AnnotatedClass): PropertyName? {
        return null
    }

    /**
     * Method for checking whether properties that have specified type (class, not generics aware) should be completely
     * ignored for serialization and deserialization purposes.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     *
     * @return `true` if properties of type should be ignored; `false` if they are not to be ignored, `null` for default
     * handling (which is 'do not ignore')
     */
    open fun isIgnorableType(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Boolean? {
        return null
    }

    /**
     * Method for finding information about properties to ignore either by name, or by more general specification
     * ("ignore all unknown"). This method combines multiple aspects of name-based (as opposed to value-based) ignorals.
     *
     * @param config Configuration settings in effect (for serialization or deserialization)
     *
     * @param annotated Annotated entity (Class, Accessor) to introspect
     */
    open fun findPropertyIgnoralByName(config: MapperConfig<*>, annotated: Annotated): CirJsonIgnoreProperties.Value? {
        return CirJsonIgnoreProperties.Value.EMPTY
    }

    /**
     * Method for finding information about names of properties to included. This is typically used to strictly limit
     * properties to include based on fully defined set of names ("allow-listing"), as opposed to excluding potential
     * properties by exclusion ("deny-listing").
     *
     * @param config Configuration settings in effect (for serialization or deserialization)
     *
     * @param annotated Annotated entity (Class, Accessor) to introspect
     */
    open fun findPropertyInclusionByName(config: MapperConfig<*>,
            annotated: Annotated): CirJsonIncludeProperties.Value? {
        return CirJsonIncludeProperties.Value.ALL
    }

    /**
     * Method for finding if annotated class has associated filter; and if so, to return id that is used to locate
     * filter.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotated Annotated entity to introspect
     *
     * @return ID of the filter to use for filtering properties of annotated class, if any; or `null` if none found.
     */
    open fun findFilterId(config: MapperConfig<*>, annotated: Annotated): Any? {
        return null
    }

    /**
     * Method for finding [PropertyNamingStrategy] for given class, if any specified by annotations; and if so, either
     * return a [PropertyNamingStrategy] instance, or Class to use for creating instance.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     *
     * @return Subclass or instance of [PropertyNamingStrategy], if one is specified for given class; `null` if not.
     */
    open fun findNamingStrategy(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        return null
    }

    /**
     * Method for finding [EnumNamingStrategy] for given class, if any specified by annotations; and if so, either
     * return a [EnumNamingStrategy] instance, or Class to use for creating instance
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     *
     * @return Subclass or instance of [EnumNamingStrategy], if one is specified for given class; `null` if not.
     */
    open fun findEnumNamingStrategy(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        return null
    }

    /**
     * Method used to check whether specified class defines a human-readable description to use for documentation. There
     * are no further definitions for contents; for example, whether these may be marked up using HTML (or something
     * like wiki format like Markup) is not defined.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     *
     * @return Human-readable description, if any.
     */
    open fun findClassDescription(config: MapperConfig<*>, annotatedClass: AnnotatedClass): String? {
        return null
    }

    /*
     *******************************************************************************************************************
     * Property auto-detection
     *******************************************************************************************************************
     */

    /**
     * Method for checking if annotations indicate changes to minimum visibility levels needed for auto-detecting
     * property elements (fields, methods, constructors). A baseline checker is given, and introspector is to either
     * return it as is (if no annotations are found), or build and return a derived instance (using checker's build
     * methods).
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Annotated class to introspect
     */
    open fun findAutoDetectVisibility(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            checker: VisibilityChecker): VisibilityChecker {
        return checker
    }

    /*
     *******************************************************************************************************************
     * Annotations for Polymorphic type handling
     *******************************************************************************************************************
     */

    /**
     * Method for checking whether given Class or Property Accessor specifies polymorphic type-handling information, to
     * indicate need for polymorphic handling.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotated Annotated entity to introspect
     */
    open fun findPolymorphicTypeInfo(config: MapperConfig<*>, annotated: Annotated): CirJsonTypeInfo.Value? {
        return null
    }

    /**
     * @param config Effective mapper configuration in use
     *
     * @param annotated Annotated entity to introspect
     */
    open fun findTypeResolverBuilder(config: MapperConfig<*>, annotated: Annotated): Any? {
        return null
    }

    /**
     * @param config Effective mapper configuration in use
     *
     * @param annotated Annotated entity to introspect
     */
    open fun findTypeIdResolver(config: MapperConfig<*>, annotated: Annotated): Any? {
        return null
    }

    /**
     * Method for locating annotation-specified subtypes related to annotated entity (class, method, field). Note that
     * this is only guaranteed to be a list of directly declared subtypes, no recursive processing is guarantees (i.e.
     * caller has to do it if/as necessary)
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotated Annotated entity (class, field/method) to check for annotations
     */
    open fun findSubtypes(config: MapperConfig<*>, annotated: Annotated): List<NamedType>? {
        return null
    }

    /**
     * Method for checking if specified type has explicit name.
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotatedClass Class to check for type name annotations
     */
    open fun findTypeName(config: MapperConfig<*>, annotatedClass: AnnotatedClass): String? {
        return null
    }

    /**
     * Method for checking whether given accessor claims to represent type id: if so, its value may be used as an
     * override, instead of generated type id.
     *
     * @param config Effective mapper configuration in use
     *
     * @param member Member to check for type id information
     */
    open fun isTypeId(config: MapperConfig<*>, member: AnnotatedMember): Boolean? {
        return null
    }

    /*
     *******************************************************************************************************************
     * General member (field, method/constructor) annotations
     *******************************************************************************************************************
     */

    /**
     * Method for checking if given member indicates that it is part of a reference (parent/child).
     *
     * @param config Effective mapper configuration in use
     *
     * @param member Member to check for information
     */
    open fun findReferenceType(config: MapperConfig<*>, member: AnnotatedMember): ReferenceProperty? {
        return null
    }

    /**
     * Method called to check whether given property is marked to be "unwrapped" when being serialized (and
     * appropriately handled in reverse direction, i.e. expect unwrapped representation during deserialization). Return
     * value is the name transformation to use, if wrapping/unwrapping should  be done, or `null` if not -- note that
     * transformation may simply be identity transformation (no changes).
     *
     * @param config Effective mapper configuration in use
     *
     * @param member Member to check for information
     */
    open fun findUnwrappingNameTransformer(config: MapperConfig<*>, member: AnnotatedMember): NameTransformer? {
        return null
    }

    /**
     * Method called to check whether given property is marked to be ignored. This is used to determine whether to
     * ignore properties, on per-property basis, usually combining annotations from multiple accessors (getters,
     * setters, fields, constructor parameters).
     *
     * @param config Effective mapper configuration in use
     *
     * @param member Member to check for information
     */
    open fun hasIgnoreMarker(config: MapperConfig<*>, member: AnnotatedMember): Boolean {
        return false
    }

    /**
     * Method called to find out whether given member expects a value to be injected, and if so, what is the identifier
     * of the value to use during injection. Type if identifier needs to be compatible with provider of values (of type
     * [InjectableValues]); often a simple String id is used.
     *
     * @param config Effective mapper configuration in use
     *
     * @param member Member to check for information
     *
     * @return Identifier of value to inject, if any; `null` if no injection indicator is found
     */
    open fun findInjectableValue(config: MapperConfig<*>, member: AnnotatedMember): CirJacksonInject.Value? {
        return null
    }

    /**
     * Method that can be called to check whether this member has an annotation that suggests whether value for matching
     * property is required or not.
     *
     * @param config Effective mapper configuration in use
     *
     * @param member Member to check for information
     */
    open fun hasRequiredMarker(config: MapperConfig<*>, member: AnnotatedMember): Boolean? {
        return null
    }

    /**
     * Method for checking if annotated property (represented by a field or getter/setter method) has definitions for
     * views it is to be included in. If `null` is returned, no view definitions exist and property is always included
     * (or always excluded as per default view inclusion configuration); otherwise it will only be included for views
     * included in returned array. View matches are checked using class inheritance rules (subclasses inherit inclusions
     * of superclasses)
     *
     * This method may also be called to find "default view(s)" for [AnnotatedClass]
     *
     * @param config Effective mapper configuration in use
     *
     * @param annotated Annotated property (represented by a method, field or ctor parameter)
     *
     * @return Array of views (represented by classes) that the property is included in; if `null`, always included
     * (same as returning array containing `Any::class`)
     */
    open fun findViews(config: MapperConfig<*>, annotated: Annotated): Array<KClass<*>>? {
        return null
    }

    /**
     * Method for finding format annotations for property or class. Return value is typically used by serializers and/or
     * deserializers to customize presentation aspects of the serialized value.
     *
     * @param config Effective mapper configuration in use
     */
    open fun findFormat(config: MapperConfig<*>, annotated: Annotated): CirJsonFormat.Value? {
        return CirJsonFormat.Value.EMPTY
    }

    /**
     * Method used to check if specified property has annotation that indicates that it should be wrapped in an element;
     * and if so, name to use. Note that not all serializers and deserializers support use this method: currently it is
     * only used by XML-backed handlers.
     *
     * @param config Effective mapper configuration in use
     *
     * @return Wrapper name to use, if any, or [PropertyName.USE_DEFAULT] to indicate that no wrapper element should be
     * used.
     */
    open fun findWrapperName(config: MapperConfig<*>, annotated: Annotated): PropertyName? {
        return null
    }

    /**
     * Method for finding suggested default value (as simple textual serialization) for the property. While core
     * databind does not make any use of it, it is exposed for extension modules to use: an expected use is generation
     * of schema representations and documentation.
     *
     * @param config Effective mapper configuration in use
     */
    open fun findPropertyDefaultValue(config: MapperConfig<*>, annotated: Annotated): String? {
        return null
    }

    /**
     * Method used to check whether specified property member (accessor or mutator) defines human-readable description
     * to use for documentation. There are no further definitions for contents; for example, whether these may be marked
     * up using HTML is not defined.
     *
     * @param config Effective mapper configuration in use
     *
     * @return Human-readable description, if any.
     */
    open fun findPropertyDescription(config: MapperConfig<*>, annotated: Annotated): String? {
        return null
    }

    /**
     * Method used to check whether specified property member (accessor or mutator) defines numeric index, and if so,
     * what is the index value. Possible use cases for index values included use by underlying data format (some binary
     * formats mandate use of index instead of name) and ordering of properties (for documentation, or during
     * serialization).
     *
     * @param config Effective mapper configuration in use
     *
     * @return Explicitly specified index for the property, if any
     */
    open fun findPropertyIndex(config: MapperConfig<*>, annotated: Annotated): Int? {
        return null
    }

    /**
     * Method for finding implicit name for a property that given annotated member (field, method, creator parameter)
     * may represent. This is different from explicit, annotation-based property name, in that it is "weak" and does not
     * either prove that a property exists (for example, if visibility is not high enough), or override explicit names.
     * In practice this method is used to introspect optional names for creator parameters (which may or may not be
     * available and cannot be detected by standard databind); or to provide alternate name mangling for fields, getters
     * and/or setters.
     *
     * @param config Effective mapper configuration in use
     */
    open fun findImplicitPropertyName(config: MapperConfig<*>, member: AnnotatedMember): String? {
        return null
    }

    /**
     * Method called to find if given property has alias(es) defined.
     *
     * @param config Effective mapper configuration in use
     *
     * @return `null` if member has no information; otherwise a `List` (possibly empty) of aliases to use.
     */
    open fun findPropertyAliases(config: MapperConfig<*>, annotated: Annotated): List<PropertyName>? {
        return null
    }

    /**
     * Method for finding optional access definition for a property, annotated
     * on one of its accessors. If a definition for read-only, write-only
     * or read-write cases, visibility rules may be modified. Note, however,
     * that even more specific annotations (like one for ignoring specific accessor)
     * may further override behavior of the access definition.
     *
     * @param config Effective mapper configuration in use
     */
    open fun findPropertyAccess(config: MapperConfig<*>, annotated: Annotated): CirJsonProperty.Access? {
        return null
    }

    /**
     * Method called in cases where a class has two methods eligible to be used for the same logical property, and
     * default logic is not enough to figure out clear precedence. Introspector may try to choose one to use; or, if
     * unable, return `null` to indicate it cannot resolve the problem.
     *
     * @param config Effective mapper configuration in use
     */
    open fun resolveSetterConflict(config: MapperConfig<*>, setter1: AnnotatedMethod,
            setter2: AnnotatedMethod): AnnotatedMethod? {
        return null
    }

    /**
     * Method called on fields that are eligible candidates for properties (that is, non-static member fields), but not
     * necessarily selected (may or may not be visible), to let fields affect name linking. Call will be made after
     * finding implicit name (which by default is just name of the field, but may be overridden by introspector), but
     * before discovering other accessors. If non-null name returned, it is to be used to find other accessors (getters,
     * setters, creator parameters) and replace their implicit names with that of field's implicit name (assuming they
     * differ).
     *
     * Specific example (and initial use case is for support Kotlin's "is getter" matching (see
     * [Kotlin Interop](https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html) for details), in which field
     * like '`isOpen`' would have implicit name of "isOpen", match getter `getOpen()` and setter `setOpen(boolean)`, but
     * use logical external name of "isOpen" (and not implicit name of getter/setter, "open"!). To achieve this, field
     * implicit name needs to remain "isOpen" but this method needs to return name `PropertyName.construct("open")`:
     * doing so will "pull in" getter and/or setter, and rename them as "isOpen".
     *
     * @param config Effective mapper configuration in use
     *
     * @param field Field to check
     *
     * @param implicitName Implicit name of the field; usually name of field itself but not always, used as the target
     * name for accessors to rename.
     *
     * @return Name used to find other accessors to rename, if any; `null` to indicate no renaming
     */
    open fun findRenameByField(config: MapperConfig<*>, field: AnnotatedField,
            implicitName: PropertyName): PropertyName? {
        return null
    }

    /*
     *******************************************************************************************************************
     * Serialization: general annotations
     *******************************************************************************************************************
     */

    /**
     * Method for getting a serializer definition on specified method or field. Type of definition is either instance
     * (of type [ValueSerializer]) or KClass (of type `KClass<ValueSerializer>` implementation subtype); if value of
     * different type is returned, a runtime exception may be thrown by caller.
     */
    open fun findSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        return null
    }

    /**
     * Method for getting a serializer definition for keys of associated `java.util.Map` property. Type of definition is
     * either instance (of type [ValueSerializer]) or KClass (of type `KClass<ValueSerializer>`); if value of different
     * type is returned, a runtime exception may be thrown by caller.
     */
    open fun findKeySerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        return null
    }

    /**
     * Method for getting a serializer definition for content (values) of associated `Collection`, `Array` or `Map`
     * property. Type of definition is either instance (of type [ValueSerializer]) or KClass (of type
     * `KClass<ValueSerializer>`); if value of different type is returned, a runtime exception may be thrown by caller.
     */
    open fun findContentSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        return null
    }

    /**
     * Method for getting a serializer definition for serializer to use for nulls (`null` values) of associated property
     * or type.
     */
    open fun findNullSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        return null
    }

    /**
     * Method for accessing declared typing mode annotated (if any). This is used for type detection, unless more
     * granular settings (such as actual exact type; or serializer to use which means no type information is needed)
     * take precedence.
     *
     * @return Typing mode to use, if annotation is found; `null` otherwise
     */
    open fun findSerializationTyping(config: MapperConfig<*>, annotated: Annotated): CirJsonSerialize.Typing? {
        return null
    }

    /**
     * Method for finding [Converter][org.cirjson.cirjackson.databind.util.Converter] that annotated entity (property or
     * class) has indicated to be used as part of serialization. If not `null`, either has to be actual
     * [Converter][org.cirjson.cirjackson.databind.util.Converter] instance, or class for such converter; and resulting
     * converter will be used first to convert property value to converter target type, and then serializer for that
     * type is used for actual serialization.
     *
     * This feature is typically used to convert internal values into types that CirJackson can convert.
     *
     * Note also that this feature does not necessarily work well with polymorphic type handling, or object identity
     * handling; if such features are needed an explicit serializer is usually better way to handle serialization.
     *
     * @param annotated Annotated property (field, method) or class to check for annotations
     */
    open fun findSerializationConverter(config: MapperConfig<*>, annotated: Annotated): Any? {
        return null
    }

    /**
     * Method for finding [Converter][org.cirjson.cirjackson.databind.util.Converter] that annotated property has
     * indicated needs to be used for values of container type (this also means that method should only be called for
     * properties of container types, List/Map/array properties).
     *
     * If not `null`, either has to be actual [Converter][org.cirjson.cirjackson.databind.util.Converter] instance, or
     * class for such converter; and resulting converter will be used first to convert property value to converter
     * target type, and then serializer for that type is used for actual serialization.
     *
     * Other notes are same as those for [findSerializationConverter]
     *
     * @param annotatedMember Annotated property (field, method) to check.
     */
    open fun findSerializationContentConverter(config: MapperConfig<*>, annotatedMember: AnnotatedMember): Any? {
        return null
    }

    /**
     * Method for checking inclusion criteria for a type (Class) or property (yes, method name is a bit unfortunate --
     * not just for properties!). In case of class, acts as the default for properties POJO contains; for properties
     * acts as override for class defaults and possible global defaults.
     */
    open fun findPropertyInclusion(config: MapperConfig<*>, annotated: Annotated): CirJsonInclude.Value? {
        return CirJsonInclude.Value.EMPTY
    }

    /*
     *******************************************************************************************************************
     * Serialization: type refinements
     *******************************************************************************************************************
     */

    /**
     * Method called to find out possible type refinements to use for deserialization, including not just value itself but key and/or content type, if type has those.
     */
    open fun refineSerializationType(config: MapperConfig<*>, annotated: Annotated, baseType: KotlinType): KotlinType {
        return baseType
    }

    /*
     *******************************************************************************************************************
     * Serialization: class annotations
     *******************************************************************************************************************
     */

    /**
     * Method for accessing defined property serialization order (which may be partial). May return `null` if no
     * ordering is defined.
     */
    open fun findSerializationContentConverter(config: MapperConfig<*>,
            annotatedClass: AnnotatedClass): Array<String>? {
        return null
    }

    /**
     * Method for checking whether an annotation indicates that serialized properties for which no explicit is defined
     * should be alphabetically (lexicographically) ordered
     */
    open fun findSerializationContentConverter(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return null
    }

    /**
     * Method for adding possible virtual properties to be serialized along with regular properties.
     */
    open fun findAndAddVirtualProperties(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            properties: MutableList<BeanPropertyWriter>) {
    }

    /*
     *******************************************************************************************************************
     * Serialization: property annotations
     *******************************************************************************************************************
     */

    /**
     * Finds the explicitly defined name of the given set of `Enum` values, if any. The method overwrites entries in the
     * incoming `names` array with the explicit names found, if any, leaving other entries unmodified.
     *
     * @param config the mapper configuration to use
     *
     * @param annotatedClass the annotated class for which to find the explicit names
     *
     * @param enumValues the set of `Enum` values to find the explicit names for
     *
     * @param names the matching declared names of enumeration values (with indexes matching `enumValues` entries)
     *
     * @return an array of names to use (possibly `names` passed as argument)
     */
    open fun findEnumValues(config: MapperConfig<*>, annotatedClass: AnnotatedClass, enumValues: Array<Enum<*>>,
            names: Array<String?>): Array<String?> {
        TODO("Not yet implemented")
    }

    /**
     * Method that is called to check if there are alternative names (aliases) that can be accepted for entries
     * in addition to primary names that were introspected earlier, related to {@link #findEnumValues}.
     * These aliases should be returned in `Array<Array<String>> aliases` passed in as argument.
     * The `aliases.size` is expected to match the number of `Enum` values.
     *
     * @param config The configuration of the mapper
     * @param annotatedClass The annotated class of the enumeration type
     * @param enumValues The values of the enumeration
     * @param aliases (in/out) Pre-allocated array where aliases found, if any, may be added (in indexes matching those
     * of `enumValues`)
     */
    open fun findEnumAliases(config: MapperConfig<*>, annotatedClass: AnnotatedClass, enumValues: Array<Enum<*>>,
            aliases: Array<Array<String>?>) {
        TODO("Not yet implemented")
    }

    /**
     * Finds the first Enum value that should be considered as default value for unknown Enum values, if present.
     *
     * @param config The configuration of the mapper
     *
     * @param annotatedClass The Enum class to scan for the default value.
     *
     * @param enumValues The Enum values of the Enum class.
     *
     * @return `null` if none found, or it's not possible to determine one.
     */
    open fun findDefaultEnumValue(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            enumValues: Array<Enum<*>>): Enum<*>? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper types
     *******************************************************************************************************************
     */

    /**
     * Value type used with managed and back references; contains type and logic name, used to link related references
     */
    open class ReferenceProperty(protected val myType: Type, protected val myName: String) {

        open val type: Type
            get() = myType

        open val name: String
            get() = myName

        open val isManagedReference: Boolean
            get() = myType == Type.MANAGED_REFERENCE

        open val isBackReference: Boolean
            get() = myType == Type.BACK_REFERENCE

        enum class Type {

            /**
             * Reference property that Jackson manages and that is serialized normally (by serializing reference
             * object), but is used for resolving back references during deserialization. Usually this can be defined by
             * using [CirJsonManagedReference]
             */
            MANAGED_REFERENCE,

            /**
             * Reference property that Jackson manages by suppressing it during serialization, and reconstructing during
             * deserialization. Usually this can be defined by using [CirJsonBackReference]
             */
            BACK_REFERENCE

        }

        companion object {

            fun managed(name: String): ReferenceProperty {
                return ReferenceProperty(Type.MANAGED_REFERENCE, name)
            }

            fun back(name: String): ReferenceProperty {
                return ReferenceProperty(Type.BACK_REFERENCE, name)
            }

        }

    }

    /**
     * Add-on extension used for XML-specific configuration, needed to decouple format module functionality from
     * pluggable introspection functionality (especially JAXB-annotation related one).
     */
    interface XmlExtensions {

        /**
         * Method that can be called to figure out generic namespace property for an annotated object.
         *
         * @param config Configuration settings in effect
         *
         * @param annotated Annotated entity to introspect
         *
         * @return `null` if annotated thing does not define any namespace information; non-`null` namespace (which may
         * be empty String) otherwise.
         */
        fun findNamespace(config: MapperConfig<*>, annotated: Annotated): String?

        /**
         * Method used to check whether given annotated element (field, method, constructor parameter) has indicator
         * that suggests it be output as an XML attribute or not (if not, then as element)
         *
         * @param config Configuration settings in effect
         *
         * @param annotated Annotated entity to introspect
         *
         * @return `null` if no indicator found; `true` or `false` otherwise
         */
        fun isOutputAsAttribute(config: MapperConfig<*>, annotated: Annotated): Boolean?

        /**
         * Method used to check whether given annotated element (field, method, constructor parameter) has indicator
         * that suggests it should be serialized as text, without element wrapper.
         *
         * @param config Configuration settings in effect
         *
         * @param annotated Annotated entity to introspect
         *
         * @return `null` if no indicator found; `true` or `false` otherwise
         */
        fun isOutputAsText(config: MapperConfig<*>, annotated: Annotated): Boolean?

        /**
         * Method used to check whether given annotated element (field, method, constructor parameter) has indicator
         * that suggests it should be wrapped in a CDATA tag.
         *
         * @param config Configuration settings in effect
         *
         * @param annotated Annotated entity to introspect
         *
         * @return `null` if no indicator found; `true` or `false` otherwise
         */
        fun isOutputAsCData(config: MapperConfig<*>, annotated: Annotated): Boolean?

    }

    companion object {

        /**
         * Factory method for accessing "no operation" implementation of introspector: instance that will never find any
         * annotation-based configuration.
         *
         * @return "no operation" instance
         */
        fun nopInstance(): AnnotationIntrospector {
            return NopAnnotationIntrospector.INSTANCE
        }

        fun pair(annotationIntrospector1: AnnotationIntrospector,
                annotationIntrospector2: AnnotationIntrospector): AnnotationIntrospector {
            return AnnotationIntrospectorPair(annotationIntrospector1, annotationIntrospector2)
        }

    }

}