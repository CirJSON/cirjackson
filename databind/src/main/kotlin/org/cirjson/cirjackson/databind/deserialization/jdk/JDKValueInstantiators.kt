package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.cirjackson.CirJsonLocationInstantiator
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Container for a set of [ValueInstantiators][ValueInstantiator] used for certain critical JDK value types, either as
 * performance optimization for initialization time observed by profiling, or due to difficulty in otherwise finding
 * constructors.
 */
object JDKValueInstantiators {

    fun findStandardValueInstantiator(raw: KClass<*>): ValueInstantiator? {
        return if (raw == CirJsonLocation::class) {
            CirJsonLocationInstantiator()
        } else if (Collection::class.isAssignableFrom(raw)) {
            when (raw) {
                ArrayList::class -> ArrayListInstantiator.INSTANCE
                HashSet::class -> HashSetInstantiator.INSTANCE
                LinkedList::class -> LinkedListInstantiator()
                TreeSet::class -> TreeSetInstantiator()
                emptySet<Any?>()::class -> ConstantValueInstantiator(emptySet<Any?>())
                emptyList<Any?>()::class -> ConstantValueInstantiator(emptyList<Any?>())
                else -> null
            }
        } else if (Map::class.isAssignableFrom(raw)) {
            when (raw) {
                LinkedHashMap::class -> LinkedHashMapInstantiator.INSTANCE
                HashMap::class -> LinkedHashMapInstantiator.INSTANCE
                ConcurrentHashMap::class -> ConcurrentHashMapInstantiator()
                TreeMap::class -> TreeMapInstantiator()
                emptyMap<Any?, Any?>()::class -> ConstantValueInstantiator(emptyMap<Any?, Any?>())
                else -> null
            }
        } else {
            null
        }
    }

    /*
     *******************************************************************************************************************
     * Base class
     *******************************************************************************************************************
     */

    private abstract class JDKValueInstantiator(type: KClass<*>) : ValueInstantiator.Base(type) {

        override fun canInstantiate(): Boolean {
            return true
        }

        override fun canCreateUsingDefault(): Boolean {
            return true
        }

        abstract override fun createUsingDefault(context: DeserializationContext): Any

    }

    /*
     *******************************************************************************************************************
     * Implementation classes
     *******************************************************************************************************************
     */

    private class ArrayListInstantiator : JDKValueInstantiator(ArrayList::class) {

        override fun createUsingDefault(context: DeserializationContext): Any {
            return ArrayList<Any?>()
        }

        companion object {

            val INSTANCE = ArrayListInstantiator()

        }

    }

    private class LinkedListInstantiator : JDKValueInstantiator(LinkedList::class) {

        override fun createUsingDefault(context: DeserializationContext): Any {
            return LinkedList<Any?>()
        }

    }

    private class HashSetInstantiator : JDKValueInstantiator(HashSet::class) {

        override fun createUsingDefault(context: DeserializationContext): Any {
            return HashSet<Any?>()
        }

        companion object {

            val INSTANCE = HashSetInstantiator()

        }

    }

    private class TreeSetInstantiator : JDKValueInstantiator(TreeSet::class) {

        override fun createUsingDefault(context: DeserializationContext): Any {
            return TreeSet<Any?>()
        }

    }

    private class ConcurrentHashMapInstantiator : JDKValueInstantiator(ConcurrentHashMap::class) {

        override fun createUsingDefault(context: DeserializationContext): Any {
            return ConcurrentHashMap<Any, Any>()
        }

    }

    private class HashMapInstantiator : JDKValueInstantiator(HashMap::class) {

        override fun createUsingDefault(context: DeserializationContext): Any {
            return HashMap<Any?, Any?>()
        }

        companion object {

            val INSTANCE = HashMapInstantiator()

        }

    }

    private class LinkedHashMapInstantiator : JDKValueInstantiator(LinkedHashMap::class) {

        override fun createUsingDefault(context: DeserializationContext): Any {
            return LinkedHashMap<Any?, Any?>()
        }

        companion object {

            val INSTANCE = LinkedHashMapInstantiator()

        }

    }

    private class TreeMapInstantiator : JDKValueInstantiator(TreeMap::class) {

        override fun createUsingDefault(context: DeserializationContext): Any {
            return TreeMap<Any?, Any?>()
        }

    }

    private class ConstantValueInstantiator(private val myValue: Any) : JDKValueInstantiator(myValue::class) {

        override fun createUsingDefault(context: DeserializationContext): Any {
            return myValue
        }

    }

}