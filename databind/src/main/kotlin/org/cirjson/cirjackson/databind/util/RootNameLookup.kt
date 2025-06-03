package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.type.ClassKey
import kotlin.reflect.KClass

/**
 * Helper class for caching resolved root names.
 */
open class RootNameLookup {

    /**
     * For efficient operation, let's try to minimize the number of times the root element name to use needs to be
     * introspected.
     */
    protected var myRootNames: LookupCache<ClassKey, PropertyName> = SimpleLookupCache(20, 200)

    fun findRootName(context: DatabindContext, rootType: KotlinType): PropertyName {
        return findRootName(context, rootType.rawClass)
    }

    fun findRootName(context: DatabindContext, rootType: KClass<*>): PropertyName {
        val key = ClassKey(rootType)
        var name = myRootNames[key]

        if (name != null) {
            return name
        }

        val annotatedClass = context.introspectClassAnnotations(rootType)
        val annotationIntrospector = context.annotationIntrospector
        name = annotationIntrospector.findRootName(context.config, annotatedClass)?.takeIf { it.hasSimpleName() }
                ?: PropertyName.construct(rootType.simpleName)
        myRootNames[key] = name
        return name
    }

}