package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.LookupCache
import org.cirjson.cirjackson.databind.util.SimpleLookupCache
import java.lang.reflect.Type
import kotlin.reflect.KClass

class TypeFactory private constructor(internal val myTypeCache: LookupCache<Any, KotlinType>,
        internal val myModifiers: Array<TypeModifier>?, private val myClassLoader: ClassLoader?) :
        Snapshottable<TypeFactory> {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    private constructor() : this(SimpleLookupCache(16, DEFAULT_MAX_CACHE_SIZE))

    private constructor(typeCache: LookupCache<Any, KotlinType>) : this(typeCache, null, null)

    override fun snapshot(): TypeFactory {
        TODO("Not yet implemented")
    }

    fun withModifier(modifier: TypeModifier?): TypeFactory {
        TODO("Not yet implemented")
    }

    fun withCache(cache: LookupCache<Any, KotlinType>): TypeFactory {
        TODO("Not yet implemented")
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

        const val DEFAULT_MAX_CACHE_SIZE = 200

        val DEFAULT_INSTANCE = TypeFactory()

        /**
         * Method for constructing a marker type that indicates missing generic type information, which is handled same
         * as simple type for `Any`.
         */
        fun unknownType(): KotlinType {
            TODO("Not yet implemented")
        }

    }

}