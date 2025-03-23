package org.cirjson.cirjackson.annotations

import org.cirjson.cirjackson.annotations.ObjectIdGenerators.IntSequenceGenerator
import org.cirjson.cirjackson.annotations.ObjectIdGenerators.PropertyGenerator
import org.cirjson.cirjackson.annotations.ObjectIdGenerators.StringIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdGenerators.UUIDGenerator
import java.util.*
import kotlin.reflect.KClass

/**
 * Container object for standard [ObjectIdGenerator] implementations:
 *
 * * [IntSequenceGenerator]
 * * [PropertyGenerator]
 * * [StringIdGenerator]
 * * [UUIDGenerator]
 *
 * NOTE: [PropertyGenerator] applicability is limited in one case: it can only be used on polymorphic base types (ones
 * indicated using [CirJsonTypeInfo] or default typing) via class annotations: property annotation will fail due to lack
 * of access to property, needed to determine type of Object ID for deserialization.
 */
object ObjectIdGenerators {

    /**
     * Shared base class for concrete implementations.
     */
    abstract class Base<T>(final override val scope: KClass<*>) : ObjectIdGenerator<T>() {

        override fun canUseFor(generator: ObjectIdGenerator<*>): Boolean {
            return generator::class.java == this::class.java && generator.scope == this.scope
        }

    }

    /**
     * Abstract marker class used to allow explicitly specifying that no generator is used; which also implies that no
     * Object ID is to be included or used.
     */
    abstract class None : ObjectIdGenerator<Any>()

    /**
     * Abstract place-holder class which is used to denote case where Object Identifier to use comes from a POJO
     * property (getter method or field). If so, value is written directly during serialization, and used as-is during
     * deserialization.
     *
     * Actual implementation class is part of `databind` package.
     */
    abstract class PropertyGenerator protected constructor(scope: KClass<*>) : Base<Any>(scope)

    /**
     * Simple sequence-number based generator, which uses basic `Int` (starting with value `1`) as Object Identifiers.
     */
    class IntSequenceGenerator(scope: KClass<*>, private var myNextValue: Int) : Base<Int>(scope) {

        constructor() : this(Any::class, -1)

        private fun initialValue() = 1

        override fun forScope(scope: KClass<*>): ObjectIdGenerator<Int> {
            if (scope == this.scope) {
                return this
            }

            return IntSequenceGenerator(scope, myNextValue)
        }

        override fun newForSerialization(context: Any): ObjectIdGenerator<Int> {
            return IntSequenceGenerator(scope, initialValue())
        }

        override fun key(key: Any?): IDKey? {
            key ?: return null
            return IDKey(this::class, scope, key)
        }

        override fun generateId(forPojo: Any?): Int? {
            forPojo ?: return null
            return myNextValue++
        }

    }

    /**
     * Implementation that just uses [UUIDs][UUID] as reliably unique identifiers: downside is that resulting String is
     * 36 characters long.
     *
     * One difference to other generators is that scope is always set as `Any::class` (regardless of arguments):
     * this because UUIDs are globally unique, and scope has no meaning.
     */
    class UUIDGenerator : Base<UUID>(Any::class) {

        /**
         * Since UUIDs are always unique, let's fully ignore scope definition
         */
        override fun canUseFor(generator: ObjectIdGenerator<*>): Boolean {
            return generator::class == this::class
        }

        /**
         * Can just return base instance since this is essentially scopeless
         */
        override fun forScope(scope: KClass<*>): ObjectIdGenerator<UUID> {
            return this
        }

        /**
         * Can just return base instance since this is essentially scopeless
         */
        override fun newForSerialization(context: Any): ObjectIdGenerator<UUID> {
            return this
        }

        override fun key(key: Any?): IDKey? {
            key ?: return null
            return IDKey(this::class, null, key)
        }

        override fun generateId(forPojo: Any?): UUID {
            return UUID.randomUUID()
        }

    }

    /**
     * Implementation that will accept arbitrary (but unique) String Ids on deserialization, and (by default) use random
     * UUID generation similar to [UUIDGenerator] for generation ids.
     *
     * This generator is most useful for cases where another system creates String Ids (of arbitrary structure, if any),
     * and CirJackson only needs to keep track of id-to-Object mapping. Generation also works, although if UUIDs are
     * always used, [UUIDGenerator] is a better match as it will also validate ids being used.
     */
    class StringIdGenerator : Base<String>(Any::class) {

        override fun canUseFor(generator: ObjectIdGenerator<*>): Boolean {
            return generator is StringIdGenerator
        }

        /**
         * Can just return base instance since this is essentially scopeless
         */
        override fun forScope(scope: KClass<*>): ObjectIdGenerator<String> {
            return this
        }

        /**
         * Can just return base instance since this is essentially scopeless
         */
        override fun newForSerialization(context: Any): ObjectIdGenerator<String> {
            return this
        }

        override fun key(key: Any?): IDKey? {
            key ?: return null
            return IDKey(this::class, null, key)
        }

        override fun generateId(forPojo: Any?): String {
            return UUID.randomUUID().toString()
        }

    }

}