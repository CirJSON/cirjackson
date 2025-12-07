package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.io.SerializedString
import org.cirjson.cirjackson.core.util.InternCache
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.util.FullyNamed

/**
 * Simple value class used for containing names of properties as defined by annotations (and possibly other
 * configuration sources).
 *
 * @property myNamespace Additional namespace, for formats that have such concept (CirJSON does not, XML does, for
 * example).
 */
open class PropertyName(simpleName: String?, protected val myNamespace: String?) : FullyNamed {

    /**
     * Basic name of the property.
     */
    protected val mySimpleName = simpleName ?: ""

    /**
     * Lazily-constructed efficient representation of the simple name.
     *
     * NOTE: not defined as volatile to avoid performance problem with concurrent access in multicore environments; due
     * to statelessness of [SerializedString] at most leads to multiple instantiations.
     */
    protected var myEncodedSimple: SerializableString? = null

    constructor(simpleName: String?) : this(simpleName, null)

    open fun internSimpleName(): PropertyName {
        if (mySimpleName.isEmpty()) {
            return this
        }

        val interned = INTERNER.intern(mySimpleName)

        if (interned == mySimpleName) {
            return this
        }

        return PropertyName(interned, myNamespace)
    }

    /**
     * Fluent factory method for constructing an instance with different simple name.
     */
    open fun withSimpleName(simpleName: String?): PropertyName {
        val realSimpleName = simpleName ?: ""

        if (realSimpleName == mySimpleName) {
            return this
        }

        return PropertyName(realSimpleName, myNamespace)
    }

    /**
     * Fluent factory method for constructing an instance with different simple name.
     */
    open fun withNamespace(namespace: String?): PropertyName {
        if (namespace == myNamespace) {
            return this
        }

        return PropertyName(mySimpleName, namespace)
    }

    /*
     *******************************************************************************************************************
     * FullyNamed implementation
     *******************************************************************************************************************
     */

    override val name: String
        get() = mySimpleName

    override val fullName: PropertyName
        get() = this

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open val simpleName: String
        get() = mySimpleName

    /**
     * Accessor that may be used to get lazily-constructed efficient representation of the simple name.
     */
    open fun simpleAsEncoded(config: MapperConfig<*>?): SerializableString {
        return myEncodedSimple ?: (config?.compileString(mySimpleName) ?: SerializedString(
                mySimpleName)).also { myEncodedSimple = it }
    }

    open val namespace: String?
        get() = myNamespace

    open fun hasSimpleName(): Boolean {
        return mySimpleName.isNotEmpty()
    }

    open fun hasSimpleName(string: String?): Boolean {
        return mySimpleName == string
    }

    open fun hasNamespace(): Boolean {
        return myNamespace != null
    }

    /**
     * Method that is basically equivalent of:
     * ```
     * !hasSimpleName() && !hasNamespace()
     * ```
     */
    open fun isEmpty(): Boolean {
        return myNamespace == null && mySimpleName.isEmpty()
    }

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is PropertyName || this::class != other::class) {
            return false
        }

        return mySimpleName == other.simpleName && myNamespace == other.namespace
    }

    override fun hashCode(): Int {
        return mySimpleName.hashCode() * 32 + myNamespace.hashCode()
    }

    override fun toString(): String {
        myNamespace ?: return mySimpleName
        return "{$myNamespace}$mySimpleName"
    }

    companion object {

        private const val USE_DEFAULT_STRING = ""

        private const val NO_NAME_STRING = ""

        /**
         * Special placeholder value that indicates that name to use should be based on the standard heuristics. This
         * can be different from returning `null`, as `null` means "no information available", whereas this value
         * indicates explicit defaulting.
         */
        val USE_DEFAULT = PropertyName(USE_DEFAULT_STRING, null)

        /**
         * Special placeholder value that indicates that there is no name associated. Exact semantics to use (if any)
         * depend on actual annotation in use, but commonly this value disables behavior for which name would be needed.
         */
        val NO_NAME = PropertyName(String(NO_NAME_STRING.toByteArray()), null)

        val INTERNER = InternCache.INSTANCE

        fun construct(simpleName: String?): PropertyName {
            if (simpleName.isNullOrEmpty()) {
                return USE_DEFAULT
            }

            return PropertyName(INTERNER.intern(simpleName), null)
        }

        fun construct(simpleName: String?, namespace: String?): PropertyName {
            val realSimpleName = simpleName ?: ""

            if (namespace == null && realSimpleName.isEmpty()) {
                return USE_DEFAULT
            }

            return PropertyName(INTERNER.intern(realSimpleName), namespace)
        }

        /**
         * Method that will combine information from two [PropertyName] instances
         *
         * @param name1 Name with higher precedence; may be `null`
         *
         * @param name2 Name with lower precedence; may be `null`
         *
         * @return Merged information; only `null` if both arguments are `null`s.
         */
        fun merge(name1: PropertyName?, name2: PropertyName?): PropertyName? {
            name1 ?: return name2
            name2 ?: return name1

            val namespace = nonEmpty(name1.namespace, name2.namespace)
            val simpleName = nonEmpty(name1.simpleName, name2.simpleName)

            if (namespace == name1.myNamespace && simpleName == name1.mySimpleName) {
                return name1
            }

            if (namespace == name2.myNamespace && simpleName == name2.mySimpleName) {
                return name2
            }

            return construct(simpleName, namespace)
        }

        private fun nonEmpty(string1: String?, string2: String?): String? {
            string1 ?: return string2
            string2 ?: return string1

            return string1.takeUnless { it.isEmpty() } ?: string2
        }

    }

}