package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.ArrayBuilders
import org.cirjson.cirjackson.databind.util.LookupCache
import org.cirjson.cirjackson.databind.util.SimpleLookupCache
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * Class used for creating concrete [KotlinType] instances, given various inputs.
 *
 * Instances of this class are accessible using [org.cirjson.cirjackson.databind.ObjectMapper] as well as many objects
 * it constructs (like [org.cirjson.cirjackson.databind.DeserializationConfig] and
 * [org.cirjson.cirjackson.databind.SerializationConfig]), but usually those objects also expose convenience methods
 * (`constructType`). So, you can do for example:
 * ```
 * val stringType = mapper.constructType(String::class)
 * ```
 * However, more advanced methods are only exposed by factory so that you may need to use:
 * ```
 * val stringCollection = mapper.typeFactory.constructCollectionType(List::class, String::class)
 * ```
 *
 * Note on optimizations: generic type parameters are resolved for all types, with following
 * exceptions:
 *
 * * For optimization purposes, type resolution is skipped for following commonly seen types that do have type
 * parameters, but ones that are rarely needed:
 *     * [Enum]: Self-referential type reference is simply dropped and Class is exposed as a simple, non-parameterized
 *     [SimpleType]
 *     * [Comparable]: Type parameter is simply dropped and interface is exposed as a simple, non-parameterized
 *     [SimpleType]
 * * For [Collection] subtypes, resolved type is ALWAYS the parameter for [Collection] and not that of actually resolved
 * subtype. This is usually (but not always) same parameter.
 * * For [Map] subtypes, resolved type is ALWAYS the parameter for [Map] and not that of actually resolved subtype.
 * These are usually (but not always) same parameters.
 *
 * @property myTypeCache Since type resolution can be expensive (specifically when resolving actual generic types), we
 * will use small cache to avoid repetitive resolution of core types
 *
 * @property myModifiers Registered [TypeModifiers][TypeModifier]: objects that can change details of [KotlinType]
 * instances factory constructs.
 *
 * @property classLoader ClassLoader used by this factory.
 */
class TypeFactory private constructor(internal val myTypeCache: LookupCache<Any, KotlinType>,
        internal val myModifiers: Array<TypeModifier>?, val classLoader: ClassLoader?) : Snapshottable<TypeFactory> {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    private constructor() : this(SimpleLookupCache(16, DEFAULT_MAX_CACHE_SIZE))

    private constructor(typeCache: LookupCache<Any, KotlinType>) : this(typeCache, null, null)

    /**
     * Need to make a copy on `snapshot()` to avoid accidental leakage via cache. In theory only needed if there are
     * modifiers, but since these are lightweight objects, let's recreate always.
     */
    override fun snapshot(): TypeFactory {
        return TypeFactory(myTypeCache.snapshot(), myModifiers, classLoader)
    }

    /**
     * "Mutant factory" method which will construct a new instance with specified [TypeModifier] added as the first
     * modifier to call (in case there are multiple registered).
     */
    fun withModifier(modifier: TypeModifier?): TypeFactory {
        var typeCache = myTypeCache
        val mods = if (modifier == null) {
            typeCache = typeCache.emptyCopy()
            null
        } else if (myModifiers == null) {
            typeCache = typeCache.emptyCopy()
            arrayOf(modifier)
        } else {
            ArrayBuilders.insertInListNoDup(myModifiers, modifier)
        }

        return TypeFactory(typeCache, mods, classLoader)
    }

    /**
     * "Mutant factory" method which will construct a new instance with specified [ClassLoader] to use by [findClass].
     */
    fun withClassLoader(classLoader: ClassLoader?): TypeFactory {
        return TypeFactory(myTypeCache, myModifiers, classLoader)
    }

    /**
     * Mutant factory method that will construct new [TypeFactory] with identical settings except for different cache.
     */
    fun withCache(cache: LookupCache<Any, KotlinType>): TypeFactory {
        return TypeFactory(cache, myModifiers, classLoader)
    }

    /**
     * Method that will clear up any cached type definitions that may be cached by this [TypeFactory] instance. This
     * method should not be commonly used, that is, only use it if you know there is a problem with retention of type
     * definitions; the most likely (and currently only known) problem is retention of [KClass] instances via
     * [KotlinType] reference.
     */
    fun clearCache() {
        myTypeCache.clear()
    }

    /*
     *******************************************************************************************************************
     * Low-level helper methods
     *******************************************************************************************************************
     */

    @Throws(ClassNotFoundException::class)
    fun findClass(className: String): KClass<*> {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public general-purpose factory methods
     *******************************************************************************************************************
     */

    fun constructType(type: Type): KotlinType {
        TODO("Not yet implemented")
    }

    fun resolveMemberType(type: Type, contextBindings: TypeBindings): KotlinType {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Actual type resolution, traversal
     *******************************************************************************************************************
     */

    internal fun fromClass(context: ClassStack?, rawType: KClass<*>, bindings: TypeBindings): KotlinType {
        TODO("Not yet implemented")
    }

    companion object {

        /**
         * Default size used to construct [typeCache].
         */
        const val DEFAULT_MAX_CACHE_SIZE = 200

        /**
         * Globally shared instance, which has no custom configuration. Used by `ObjectMapper` to get the default
         * factory when constructed.
         */
        val DEFAULT_INSTANCE = TypeFactory()

        /*
         ***************************************************************************************************************
         * Constants for "well-known" classes
         ***************************************************************************************************************
         */

        private val CLASS_STRING = String::class

        private val CLASS_ANY = Any::class

        private val CLASS_COMPARABLE = Comparable::class

        private val CLASS_ENUM = Enum::class

        private val CLASS_CIRJSON_NODE = CirJsonNode::class

        private val CLASS_BOOLEAN = Boolean::class

        private val CLASS_DOUBLE = Double::class

        private val CLASS_INT = Int::class

        private val CLASS_LONG = Long::class

        /*
         ***************************************************************************************************************
         * Cached pre-constructed KotlinType instances
         ***************************************************************************************************************
         */

        private val CORE_TYPE_BOOLEAN = SimpleType.construct(CLASS_BOOLEAN)

        private val CORE_TYPE_DOUBLE = SimpleType.construct(CLASS_DOUBLE)

        private val CORE_TYPE_INT = SimpleType.construct(CLASS_INT)

        private val CORE_TYPE_LONG = SimpleType.construct(CLASS_LONG)

        private val CORE_TYPE_STRING = SimpleType.construct(CLASS_STRING)

        /**
         * Cache [Comparable] because it is both parametric (relatively costly to resolve) and mostly useless (no
         * special handling), better handle directly
         */
        private val CORE_TYPE_COMPARABLE = SimpleType.construct(CLASS_COMPARABLE)

        /**
         * Cache [Enum] because it is parametric AND self-referential (costly to resolve) and useless in itself (no
         * special handling).
         */
        private val CORE_TYPE_ENUM = SimpleType.construct(CLASS_ENUM)

        /**
         * Cache [CirJsonNode] because it is no critical path of simple tree model reading and does not have things to
         * override
         */
        private val CORE_TYPE_CIRJSON_NODE = SimpleType.construct(CLASS_CIRJSON_NODE)

        /*
         ***************************************************************************************************************
         * Static methods for non-instance-specific functionality
         ***************************************************************************************************************
         */

        /**
         * Method for constructing a marker type that indicates missing generic type information, which is handled same
         * as simple type for `Any`.
         */
        fun unknownType(): KotlinType {
            TODO("Not yet implemented")
        }

    }

}