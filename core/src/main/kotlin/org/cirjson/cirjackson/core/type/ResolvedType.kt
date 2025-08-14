package org.cirjson.cirjackson.core.type

import kotlin.reflect.KClass

abstract class ResolvedType {

    /**
     * Type-erased [Class] of resolved type
     */
    abstract val rawClass: KClass<*>

    abstract fun hasRawClass(clazz: KClass<*>): Boolean

    abstract val isAbstract: Boolean

    abstract val isConcrete: Boolean

    abstract val isThrowable: Boolean

    abstract val isArrayType: Boolean

    abstract val isEnumType: Boolean

    abstract val isInterface: Boolean

    abstract val isPrimitive: Boolean

    abstract val isFinal: Boolean

    abstract val isContainerType: Boolean

    abstract val isCollectionLikeType: Boolean

    /**
     * Whether this type is a referential type, meaning that values are basically pointers to "real" values (or `null`)
     * and not regular values themselves. Typical examples include things like
     * [java.util.concurrent.atomic.AtomicReference], and various `Optional` types (in JDK8, Guava).
     */
    val isReferenceType: Boolean
        get() = referencedType != null

    abstract val isMapLikeType: Boolean

    /*
     *******************************************************************************************************************
     * Public API, type parameter access
     *******************************************************************************************************************
     */

    /**
     * Accessor that can be used to find out if the type directly declares generic parameters (for its direct
     * super-class and/or super-interfaces).
     */
    abstract val hasGenericTypes: Boolean

    /**
     * Accessor for key type for this type, assuming type has such a concept (only Map types do)
     */
    abstract val keyType: ResolvedType?

    /**
     * Accessor for the content type of this type, if type has such a thing: simple types do not, structured types do
     * (like arrays, Collections and Maps)
     */
    abstract val contentType: ResolvedType?

    /**
     * Accessor for the type of value that instances of this type references, if any.
     */
    abstract val referencedType: ResolvedType?

    /**
     * Method for checking how many contained types this type has. Contained types are usually generic types, so that
     * generic Maps have 2 contained types.
     *
     * @return Number of contained types that may be accessed
     */
    abstract fun containedTypeCount(): Int

    /**
     * Method for accessing definitions of contained ("child") types.
     *
     * @param index Index of the contained type to return
     *
     * @return Contained type at index, or `null` if no such type exists (no exception thrown)
     */
    abstract fun containedType(index: Int): ResolvedType?

    /*
     *******************************************************************************************************************
     * Public API, type parameter access
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to serialize type into form from which it can be fully deserialized from at a later point
     * (using `TypeFactory` from mapper package). For simple types, this is the same as calling [KClass.qualifiedName],
     * but for structured types it may additionally contain type information about contents.
     *
     * @return String representation of the fully resolved type
     */
    abstract fun toCanonical(): String

}