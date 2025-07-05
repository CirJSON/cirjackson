package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.util.Snapshottable

/**
 * Helper class used for storing and accessing per-call attributes. Storage is two-layered: at higher precedence, we
 * have actual per-call attributes; and at lower precedence, default attributes that may be defined for Object readers
 * and writers.
 *
 * Note that the way mutability is implemented differs between kinds of attributes, to account for thread-safety:
 * per-call attributes are handled assuming that instances are never shared, whereas changes to per-reader/per-writer
 * attributes are made assuming sharing, by creating new copies instead of modifying state. This allows sharing of
 * default values without per-call copying, but requires two-level lookup on access.
 */
abstract class ContextAttributes : Snapshottable<ContextAttributes> {

    /*
     *******************************************************************************************************************
     * Per-reader/writer access
     *******************************************************************************************************************
     */

    abstract fun withSharedAttribute(key: Any, value: Any): ContextAttributes

    abstract fun withSharedAttributes(attributes: Map<*, *>): ContextAttributes

    abstract fun withoutSharedAttribute(key: Any): ContextAttributes

    /*
     *******************************************************************************************************************
     * Per-operation (serialize/deserialize) access
     *******************************************************************************************************************
     */

    /**
     * Accessor for value of specified attribute
     */
    abstract fun getAttribute(key: Any): Any?

    /**
     * Mutator used during call (via context) to set the value of "non-shared" part of attribute set.
     */
    abstract fun withPerCallAttribute(key: Any, value: Any?): ContextAttributes

    /*
     *******************************************************************************************************************
     * Default implementation
     *******************************************************************************************************************
     */

    open class Implementation : ContextAttributes {

        /**
         * Shared attributes that we cannot modify in-place.
         */
        protected val myShared: Map<*, *>

        /**
         * Per-call attributes that we can directly modify, since they are not shared between threads.
         *
         * NOTE: typed as Object-to-Object, unlike [myShared], because we need to be able to modify contents, and a
         * wildcard type would complicate that access.
         */
        protected var myNonShared: MutableMap<Any, Any>?

        /*
         ***************************************************************************************************************
         * Construction, factory methods
         ***************************************************************************************************************
         */

        protected constructor(shared: Map<*, *>) {
            myShared = shared
            myNonShared = null
        }

        protected constructor(shared: Map<*, *>, nonShared: MutableMap<Any, Any>?) {
            myShared = shared
            myNonShared = nonShared
        }

        override fun snapshot(): ContextAttributes {
            val copyOfShared = copy(myShared)
            return Implementation(copyOfShared)
        }

        /*
         ***************************************************************************************************************
         * Per-reader/writer mutant factories
         ***************************************************************************************************************
         */

        override fun withSharedAttribute(key: Any, value: Any): ContextAttributes {
            val map = if (this === EMPTY) {
                HashMap(8)
            } else {
                copy(myShared)
            }

            map[key] = value
            return Implementation(map)
        }

        override fun withSharedAttributes(attributes: Map<*, *>): ContextAttributes {
            return Implementation(attributes)
        }

        override fun withoutSharedAttribute(key: Any): ContextAttributes {
            if (myShared.isEmpty()) {
                return this
            }

            if (key !in myShared) {
                return this
            }

            if (myShared.size == 1) {
                return EMPTY
            }

            val map = copy(myShared)
            map.remove(key)
            return Implementation(map)
        }

        /*
         ***************************************************************************************************************
         * Per-call access
         ***************************************************************************************************************
         */

        override fun getAttribute(key: Any): Any? {
            val nonShared = myNonShared ?: return myShared[key]
            val value = nonShared[key] ?: return myShared[key]
            return value.takeUnless { NULL_SURROGATE === it }
        }

        override fun withPerCallAttribute(key: Any, value: Any?): ContextAttributes {
            var realValue = value

            if (realValue == null) {
                if (key in myShared) {
                    realValue = NULL_SURROGATE
                } else if (myNonShared == null || key !in myNonShared!!) {
                    return this
                } else {
                    myNonShared!!.remove(key)
                    return this
                }
            }

            if (myNonShared == null) {
                return nonSharedInstance(key, realValue)
            }

            myNonShared!![key] = realValue
            return this
        }

        /*
         ***************************************************************************************************************
         * Internal methods
         ***************************************************************************************************************
         */

        /**
         * Overridable method that creates initial non-shared instance, with the first explicit set value.
         */
        protected open fun nonSharedInstance(key: Any, value: Any?): ContextAttributes {
            val map = HashMap<Any, Any>()
            map[key] = value ?: NULL_SURROGATE
            return Implementation(myShared, map)
        }

        @Suppress("UNCHECKED_CAST")
        private fun copy(source: Map<*, *>): MutableMap<Any, Any> {
            return HashMap(source as Map<Any, Any>)
        }

        companion object {

            val EMPTY = Implementation(emptyMap<Any, Any>())

            val NULL_SURROGATE = Any()

        }

    }

    companion object {

        val EMPTY: ContextAttributes = Implementation.EMPTY

    }

}