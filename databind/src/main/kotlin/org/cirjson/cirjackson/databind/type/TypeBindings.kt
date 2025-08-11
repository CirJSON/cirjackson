package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.hasClass
import kotlin.reflect.KClass
import kotlin.reflect.KTypeParameter

/**
 * Helper class used for resolving type parameters for given class
 *
 * @property myUnboundVariables Names of potentially unresolved type variables.
 */
open class TypeBindings private constructor(names: Array<String?>?, types: Array<KotlinType?>?,
        private val myUnboundVariables: Array<String?>?) {

    /**
     * Array of type (type variable) names.
     */
    private val myNames = names ?: NO_STRINGS

    /**
     * Types matching names
     */
    private val myTypes = types ?: NO_TYPES

    private val myHashCode = myTypes.contentHashCode()

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    init {
        if (myNames.size != myTypes.size) {
            throw IllegalArgumentException("Mismatching names (${myNames.size}), types (${myTypes.size})")
        }
    }

    /**
     * Method for creating an instance that has same bindings as this object, plus an indicator for additional type
     * variable that may be unbound within this context; this is needed to resolve recursive self-references.
     */
    open fun withUnboundVariable(name: String): TypeBindings {
        val length = myUnboundVariables?.size ?: 0
        val unboundVariables = myUnboundVariables?.takeUnless { length > 0 }?.copyOf(length + 1) ?: arrayOfNulls(1)
        return TypeBindings(myNames, myTypes, unboundVariables)
    }

    /**
     * Create a new instance with the same bindings as this object, except with the given variable removed. This is used
     * to create generic types that are "partially raw", i.e. only have some variables bound.
     */
    open fun withoutVariable(name: String): TypeBindings {
        val index = myNames.indexOf(name)

        if (index == -1) {
            return this
        }

        val newTypes = myTypes.copyOf()
        newTypes[index] = null
        return TypeBindings(myNames, newTypes, myUnboundVariables)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    /**
     * Find type bound to specified name, if there is one; returns bound type if so, `null` if not.
     */
    open fun findBoundType(name: String): KotlinType? {
        for (i in myNames.indices) {
            if (name == myNames[i]) {
                var type = myTypes[i]

                if (type is ResolvedRecursiveType) {
                    val otherType = type.selfReferencedType

                    if (otherType != null) {
                        type = otherType
                    }
                }

                return type
            }
        }

        return null
    }

    /**
     * Returns `true` if a shallow search of the type bindings includes a placeholder type which uses reference
     * equality, thus cannot produce cache hits. This is an optimization to avoid churning memory in the cache
     * unnecessarily. Note that it is still possible for nested type information to contain such placeholder types (see
     * `NestedTypes1604Test` for an example) so it's vital that they produce a distribution of hashCode values, even if
     * they may push reusable data out of the cache.
     */
    private fun invalidCacheKey(): Boolean {
        for (type in myTypes) {
            if (type is IdentityEqualityType) {
                return true
            }
        }

        return false
    }

    open fun isEmpty(): Boolean {
        return myTypes.isEmpty()
    }

    /**
     * Returns number of bindings contained
     */
    open val size
        get() = myTypes.size

    open fun getBoundName(index: Int): String? {
        return myNames.getOrNull(index)
    }

    /**
     * Get the type bound to the variable at `index`. If the type is [not bound][withoutVariable] but the index is
     * within [size] constraints, this method returns [TypeFactory.unknownType] for compatibility. If the index is out
     * of [size] constraints, this method will still return `null`.
     */
    open fun getBoundType(index: Int): KotlinType? {
        if (index !in myTypes.indices) {
            return null
        }

        return myTypes[index] ?: TypeFactory.unknownType()
    }

    /**
     * Get the type bound to the variable at `index`. If the type is [not bound][withoutVariable] or the index is within
     * [size] constraints, this method returns `null`.
     */
    open fun getBoundTypeOrNull(index: Int): KotlinType? {
        return myTypes.getOrNull(index)
    }

    /**
     * Accessor for getting bound types in declaration order
     */
    open val typeParameters: List<KotlinType>
        get() {
            if (myTypes.isEmpty()) {
                return emptyList()
            }

            return Array(myTypes.size) { myTypes[it] ?: TypeFactory.unknownType() }.toList()
        }

    open fun hasUnbound(name: String): Boolean {
        return myUnboundVariables?.contains(name) ?: false
    }

    /**
     * Factory method that will create an object that can be used as a key for
     * caching purposes by [TypeFactory]
     *
     * @return An object which can be used as a key in TypeFactory, or `null` if no key can be created.
     */
    open fun asKey(rawBase: KClass<*>): Any? {
        if (invalidCacheKey()) {
            return null
        }

        return AsKey(rawBase, myTypes, myHashCode)
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        if (myTypes.isEmpty()) {
            return "<>"
        }

        val stringBuilder = StringBuilder()
        stringBuilder.append('<')

        for (i in myTypes.indices) {
            if (i > 0) {
                stringBuilder.append(',')
            }

            val type = myTypes[i]

            if (type != null) {
                stringBuilder.append(type.genericSignature)
            } else {
                stringBuilder.append('?')
            }
        }

        stringBuilder.append('>')
        return stringBuilder.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other == null) {
            return false
        }

        if (!other.hasClass(this::class)) {
            return false
        }

        return myHashCode == (other as TypeBindings).myHashCode && myTypes.contentEquals(other.myTypes)
    }

    override fun hashCode(): Int {
        return myHashCode
    }

    /*
     *******************************************************************************************************************
     * Package accessible methods
     *******************************************************************************************************************
     */

    internal fun typeParameterArray(): Array<KotlinType?> {
        return myTypes
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Helper class that contains simple logic for avoiding repeated lookups via [KClass.typeParameters] as that can be
     * a performance issue for some use cases (wasteful, usually one-off or not reusing mapper). Partly isolated to
     * avoid initialization for cases where no generic types are used.
     */
    internal object TypeParametersStash {

        private val ABSTRACT_LIST = AbstractList::class.typeParameters

        private val COLLECTION = Collection::class.typeParameters

        private val ITERABLE = Iterable::class.typeParameters

        private val LIST = List::class.typeParameters

        private val ARRAY_LIST = ArrayList::class.typeParameters

        private val MAP = Map::class.typeParameters

        private val HASH_MAP = HashMap::class.typeParameters

        private val LINKED_HASH_MAP = LinkedHashMap::class.typeParameters

        fun paramsFor1(erasedType: KClass<*>): List<KTypeParameter> {
            return when (erasedType) {
                Collection::class -> COLLECTION
                List::class -> LIST
                ArrayList::class -> ARRAY_LIST
                AbstractList::class -> ABSTRACT_LIST
                Iterable::class -> ITERABLE
                else -> erasedType.typeParameters
            }
        }

        fun paramsFor2(erasedType: KClass<*>): List<KTypeParameter> {
            return when (erasedType) {
                Map::class -> MAP
                HashMap::class -> HASH_MAP
                LinkedHashMap::class -> LINKED_HASH_MAP
                else -> erasedType.typeParameters
            }
        }

    }

    /**
     * Helper type used to allow caching of generic types
     */
    internal class AsKey(private val myRaw: KClass<*>, private val myParams: Array<KotlinType?>, hash: Int) {

        private val myHash = 31 * myRaw.hashCode() + hash

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }

            if (other !is AsKey) {
                return false
            }

            if (myHash != other.myHash || myRaw != other.myRaw) {
                return false
            }

            val otherParams = other.myParams
            val length = myParams.size

            if (length != otherParams.size) {
                return false
            }

            for (i in 0 until length) {
                if (myParams[i] != otherParams[i]) {
                    return false
                }
            }

            return true
        }

        override fun hashCode(): Int {
            return myHash
        }

        override fun toString(): String {
            return "${myRaw.qualifiedName}<>"
        }

    }

    companion object {

        private val NO_STRINGS = emptyArray<String?>()

        private val NO_TYPES = emptyArray<KotlinType?>()

        val EMPTY = TypeBindings(null, null, null)

        /**
         * Factory method for constructing bindings for given class using specified type parameters.
         */
        fun create(erasedType: KClass<*>, typeList: List<KotlinType?>?): TypeBindings {
            return create(erasedType, typeList?.takeUnless { it.isEmpty() }?.toTypedArray() ?: NO_TYPES)
        }

        fun create(erasedType: KClass<*>, types: Array<KotlinType?>?): TypeBindings {
            val realTypes = types ?: NO_TYPES

            when (realTypes.size) {
                1 -> return create(erasedType, realTypes[0]!!)
                2 -> return create(erasedType, realTypes[0]!!, realTypes[1]!!)
            }

            val vars = erasedType.typeParameters
            val names = if (vars.isEmpty()) {
                NO_STRINGS
            } else {
                Array<String?>(vars.size) { vars[it].name }
            }

            if (names.size != vars.size) {
                throw IllegalArgumentException(
                        "Cannot create TypeBindings for class ${erasedType.qualifiedName} with ${realTypes.size} type parameters: class expects ${names.size}")
            }

            return TypeBindings(names, realTypes, null)
        }

        fun create(erasedType: KClass<*>, typeArg1: KotlinType): TypeBindings {
            val vars = TypeParametersStash.paramsFor1(erasedType)

            if (vars.size != 1) {
                throw IllegalArgumentException(
                        "Cannot create TypeBindings for class ${erasedType.qualifiedName} with 1 type parameter: class expects ${vars.size}")
            }

            return TypeBindings(arrayOf(vars[0].name), arrayOf(typeArg1), null)
        }

        fun create(erasedType: KClass<*>, typeArg1: KotlinType, typeArg2: KotlinType): TypeBindings {
            val vars = TypeParametersStash.paramsFor1(erasedType)

            if (vars.size != 2) {
                throw IllegalArgumentException(
                        "Cannot create TypeBindings for class ${erasedType.qualifiedName} with 2 type parameter: class expects ${vars.size}")
            }

            return TypeBindings(arrayOf(vars[0].name, vars[1].name), arrayOf(typeArg1, typeArg2), null)
        }

        /**
         * Factory method for constructing bindings given names and associated types.
         */
        fun create(names: List<String>?, types: List<KotlinType>?): TypeBindings {
            if (names.isNullOrEmpty() || types.isNullOrEmpty()) {
                return EMPTY
            }

            return TypeBindings(names.toTypedArray(), types.toTypedArray(), null)
        }

        /**
         * Alternate factory method that may be called if it is possible that type does or does not require type
         * parameters; this is mostly useful for collection- and map-like types.
         */
        fun createIfNeeded(erasedType: KClass<*>, typeArg1: KotlinType): TypeBindings {
            val vars = erasedType.typeParameters

            if (vars.isEmpty()) {
                return EMPTY
            }

            if (vars.size != 1) {
                throw IllegalArgumentException(
                        "Cannot create TypeBindings for class ${erasedType.qualifiedName} with 1 type parameter: class expects ${vars.size}")
            }

            return TypeBindings(arrayOf(vars[0].name), arrayOf(typeArg1), null)
        }

        /**
         * Alternate factory method that may be called if it is possible that type does or does not require type
         * parameters; this is mostly useful for collection- and map-like types.
         */
        fun createIfNeeded(erasedType: KClass<*>, types: Array<KotlinType?>?): TypeBindings {
            val vars = erasedType.typeParameters

            if (vars.isEmpty()) {
                return EMPTY
            }

            val realTypes = types ?: NO_TYPES
            val length = vars.size
            val names = Array<String?>(length) { vars[it].name }

            if (names.size != vars.size) {
                throw IllegalArgumentException(
                        "Cannot create TypeBindings for class ${erasedType.qualifiedName} with ${realTypes.size} type parameter${"s".takeUnless { realTypes.size == 1 } ?: ""}: class expects ${names.size}")
            }

            return TypeBindings(names, realTypes, null)
        }

    }

}