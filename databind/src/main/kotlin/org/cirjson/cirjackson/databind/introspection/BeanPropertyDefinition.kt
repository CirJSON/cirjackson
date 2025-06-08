package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyMetadata
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.util.FullyNamed
import org.cirjson.cirjackson.databind.util.emptyIterator
import kotlin.reflect.KClass

/**
 * Simple value classes that contain definitions of properties, used during introspection of properties to use for
 * serialization and deserialization. These instances are created before actual
 * [BeanProperty][org.cirjson.cirjackson.databind.BeanProperty] instances are created, i.e., they are used earlier in
 * the process flow, and are typically used to construct actual
 * [BeanProperty][org.cirjson.cirjackson.databind.BeanProperty] instances.
 */
abstract class BeanPropertyDefinition : FullyNamed {

    /*
     *******************************************************************************************************************
     * Fluent factory methods for creating modified copies
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to create a definition with the same settings as this one, but with different (external)
     * name; that is, one for which [name] would return `newName`.
     */
    abstract fun withName(newName: PropertyName): BeanPropertyDefinition

    /**
     * Alternate "mutant factory" that will only change simple name, but leave other optional parts (like namespace) as
     * is.
     */
    abstract fun withSimpleName(newSimpleName: String?): BeanPropertyDefinition

    /*
     *******************************************************************************************************************
     * Property name information, other
     *******************************************************************************************************************
     */

    /**
     * Accessor that can be used to determine implicit name from underlying element(s) before possible renaming. This is
     * the "internal" name derived from accessor ("x" from "getX"), and is not based on annotations or naming strategy.
     */
    abstract val internalName: String

    /**
     * Accessor for finding wrapper name to use for property (if any).
     */
    abstract val wrapperName: PropertyName?

    /**
     * Accessor that can be called to check whether property was included due to an explicit marker (usually annotation)
     * or just by naming convention.
     *
     * @return `true` if property was explicitly included (usually by having one of the components being annotated);
     * `false` if inclusion was purely due to naming or visibility definitions (that is, implicit)
     */
    abstract val isExplicitlyIncluded: Boolean

    /**
     * Accessor that can be called to check whether property name was due to an explicit marker (usually annotation), or
     * just by naming convention or use of "use-default-name" marker (annotation).
     *
     * Note that entries that return `true` from this method will always return `true` for [isExplicitlyIncluded], but
     * not necessarily vice versa.
     */
    open val isExplicitlyNamed: Boolean
        get() = isExplicitlyIncluded

    /*
     *******************************************************************************************************************
     * Basic property metadata
     *******************************************************************************************************************
     */

    abstract val primaryType: KotlinType

    abstract val rawPrimaryType: KClass<*>

    /**
     * Accessor to get additional metadata. NOTE: will never return `null`, so dereferencing return value is safe.
     */
    abstract val metadata: PropertyMetadata

    /**
     * Accessor that can be called to check if this property is expected to have a value; and if none found, should
     * either be considered invalid (and most likely fail deserialization), or handled by other means (by providing
     * default value)
     */
    open val isRequired: Boolean
        get() = metadata.isRequired

    /*
     *******************************************************************************************************************
     * Capabilities
     *******************************************************************************************************************
     */

    open fun couldDeserialize(): Boolean {
        return mutator != null
    }

    open fun couldSerialize(): Boolean {
        return accessor != null
    }

    /*
     *******************************************************************************************************************
     * Access to accessors (fields, methods, etc.)
     *******************************************************************************************************************
     */

    abstract fun hasGetter(): Boolean

    abstract fun hasSetter(): Boolean

    abstract fun hasField(): Boolean

    abstract fun hasConstructorParameter(): Boolean

    abstract val getter: AnnotatedMethod?

    abstract val setter: AnnotatedMethod?

    abstract val field: AnnotatedField?

    abstract val constructorParameter: AnnotatedParameter?

    /**
     * Additional method that may be called instead of [constructorParameter] to get access to all constructor
     * parameters, not just the highest priority one.
     */
    open val constructorParameters: Iterator<AnnotatedParameter>
        get() = emptyIterator()

    /**
     * Accessor that can be called to find accessor (getter, field to access) to use for accessing the value of the
     * property. `null` if no such member exists.
     */
    val accessor: AnnotatedMember?
        get() = getter ?: this.field

    /**
     * Accessor that can be called to find mutator (constructor parameter, setter, field) to use for changing value of
     * the property. `null` if no such member exists.
     */
    val mutator: AnnotatedMember?
        get() = constructorParameter ?: setter ?: this.field

    val nonConstructorMutator: AnnotatedMember?
        get() = constructorParameter ?: setter ?: this.field

    /**
     * Accessor that can be called to find the property member (getter, setter, field) that has the highest precedence
     * in the current context (getter method when serializing, if available, and so forth), if any.
     *
     * Note: may throw [IllegalArgumentException] in case problems are found trying to getter or setter info.
     */
    abstract val primaryMember: AnnotatedMember?

    /*
     *******************************************************************************************************************
     * More refined access to configuration features (usually based on annotations and/or config overrides). Since most
     * trivial implementations do not support these methods, they are implemented as no-ops.
     *******************************************************************************************************************
     */

    /**
     * Method used to find View-inclusion definitions for the property.
     */
    open fun findViews(): Array<KClass<*>>? {
        return null
    }

    /**
     * Method used to find whether property is part of a bidirectional reference.
     */
    open fun findReferenceType(): AnnotationIntrospector.ReferenceProperty? {
        return null
    }

    open fun findReferenceName(): String? {
        return findReferenceType()?.name
    }

    /**
     * Accessor that can be called to check whether this logical property has a marker to indicate it should be used as
     * the type id for polymorphic type handling.
     */
    open val isTypeId = false

    /**
     * Method used to check whether this logical property indicates that value POJOs should be written using additional
     * Object Identifier (or, when multiple references exist, all but first AS Object Identifier).
     */
    open fun findObjectIdInfo(): ObjectIdInfo? {
        return null
    }

    /**
     * Method used to check if this property has specific inclusion override associated with it or not. It should NOT
     * check for any default settings (global, per-type, or containing POJO settings)
     */
    abstract fun findInclusion(): CirJsonInclude.Value

    /**
     * Method for finding all aliases of the property, if any.
     *
     * @return [List] of aliases, if any; never `null` (empty list if no aliases found)
     */
    abstract fun findAliases(): List<PropertyName>

}