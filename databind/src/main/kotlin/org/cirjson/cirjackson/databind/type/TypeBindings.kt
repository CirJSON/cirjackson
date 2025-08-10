package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass
import kotlin.reflect.KTypeParameter

/**
 * Helper class used for resolving type parameters for given class
 *
 * @property myUnboundVariables Names of potentially unresolved type variables.
 */
open class TypeBindings private constructor(names: Array<String>?, types: Array<KotlinType>?,
        private val myUnboundVariables: Array<String>?) {

    /**
     * Array of type (type variable) names.
     */
    private val myNames = names ?: NO_STRINGS

    /**
     * Types matching names
     */
    private val myTypes = types ?: NO_TYPES

    private val myHashCode = myTypes.contentHashCode()

    init {
        if (myNames.size != myTypes.size) {
            throw IllegalArgumentException("Mismatching names (${myNames.size}), types (${myTypes.size})")
        }
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
    internal class AsKey(private val myRaw: KClass<*>, private val myParams: Array<KotlinType>, hash: Int) {

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

        val NO_STRINGS = emptyArray<String>()

        val NO_TYPES = emptyArray<KotlinType>()

        val EMPTY = TypeBindings(null, null, null)

        /**
         * Factory method for constructing bindings for given class using specified type parameters.
         */
        fun create(erasedType: KClass<*>, typeList: List<KotlinType>?): TypeBindings {
            return create(erasedType, typeList?.takeUnless { it.isEmpty() }?.toTypedArray() ?: NO_TYPES)
        }

        fun create(erasedType: KClass<*>, types: Array<KotlinType>?): TypeBindings {
            val realTypes = types ?: NO_TYPES

            when (realTypes.size) {
                1 -> return create(erasedType, realTypes[0])
                2 -> return create(erasedType, realTypes[0], realTypes[1])
            }

            val vars = erasedType.typeParameters
            val names = if (vars.isEmpty()) {
                NO_STRINGS
            } else {
                Array(vars.size) { vars[it].name }
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
        fun createIfNeeded(erasedType: KClass<*>, types: Array<KotlinType>?): TypeBindings {
            val vars = erasedType.typeParameters

            if (vars.isEmpty()) {
                return EMPTY
            }

            val realTypes = types ?: NO_TYPES
            val length = vars.size
            val names = Array(length) { vars[it].name }

            if (names.size != vars.size) {
                throw IllegalArgumentException(
                        "Cannot create TypeBindings for class ${erasedType.qualifiedName} with ${realTypes.size} type parameter${"s".takeUnless { realTypes.size == 1 } ?: ""}: class expects ${names.size}")
            }

            return TypeBindings(names, realTypes, null)
        }

    }

}