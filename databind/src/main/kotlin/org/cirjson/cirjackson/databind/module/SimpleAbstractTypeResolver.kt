package org.cirjson.cirjackson.databind.module

import org.cirjson.cirjackson.databind.AbstractTypeResolver
import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.type.ClassKey
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import kotlin.reflect.KClass

/**
 * Simple [AbstractTypeResolver] implementation, which is based on static mapping from abstract super types into
 * subtypes (concrete or abstract), but retaining generic parameterization. Can be used for things like specifying which
 * implementation of [Collection] to use:
 * ```
 * val resolver = SimpleAbstractTypeResolver()
 * // To make all properties declared as Collection, List, to LinkedList
 * resolver.addMapping(Collection::class, LinkedList::class)
 * resolver.addMapping(List::class, LinkedList::class)
 * ```
 * Can also be used as an alternative to per-class annotations when defining concrete implementations; however, only
 * works with abstract types (since this is only called for abstract types)
 */
open class SimpleAbstractTypeResolver : AbstractTypeResolver() {

    /**
     * Mappings from super types to subtypes
     */
    protected val myMappings = HashMap<ClassKey, KClass<*>>()

    /**
     * Method for adding a mapping from super type to specific subtype. Arguments will be checked by method, to ensure
     * that [supertype] is abstract (since resolver is never called for concrete classes); as well as to ensure that
     * there is supertype/subtype relationship (to ensure there won't be cycles during resolution).
     *
     * @param supertype Abstract type to resolve
     *
     * @param subtype Subclass of [supertype], to map superTo to
     *
     * @return This resolver, to allow chaining of initializations
     */
    open fun <T : Any> addMapping(supertype: KClass<T>, subtype: KClass<out T>): SimpleAbstractTypeResolver {
        if (supertype == subtype) {
            throw IllegalArgumentException("Cannot add mapping from class to itself")
        } else if (!supertype.isAssignableFrom(subtype)) {
            throw IllegalArgumentException(
                    "Cannot add mapping from class ${supertype.qualifiedName} to ${subtype.qualifiedName}, as latter is not a subtype of former")
        } else if (!supertype.isAbstract) {
            throw IllegalArgumentException(
                    "Cannot add mapping from class ${supertype.qualifiedName} since it is not abstract")
        }

        myMappings[ClassKey(supertype)] = subtype
        return this
    }

    /*
     *******************************************************************************************************************
     * AbstractTypeResolver implementation
     *******************************************************************************************************************
     */

    override fun findTypeMapping(config: DeserializationConfig, type: KotlinType): KotlinType? {
        val source = type.rawClass
        val destination = myMappings[ClassKey(source)] ?: return null
        return config.typeFactory.constructSpecializedType(type, destination)
    }

    override fun resolveAbstractType(config: DeserializationConfig, typeDescription: BeanDescription): KotlinType? {
        return null
    }

}