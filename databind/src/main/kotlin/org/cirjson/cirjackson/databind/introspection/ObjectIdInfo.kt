package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdResolver
import org.cirjson.cirjackson.annotations.SimpleObjectIdResolver
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.util.name
import kotlin.reflect.KClass

/**
 * Container object that encapsulates information usually derived from
 * [CirJsonIdentityInfo][org.cirjson.cirjackson.annotations.CirJsonIdentityInfo] annotation or its custom alternatives
 */
open class ObjectIdInfo protected constructor(protected val myPropertyName: PropertyName,
        protected val myScope: KClass<*>?, protected val myGenerator: KClass<out ObjectIdGenerator<*>>?,
        protected val myAlwaysAsId: Boolean, resolver: KClass<out ObjectIdResolver>?) {

    protected val myResolver = resolver ?: SimpleObjectIdResolver::class

    constructor(name: PropertyName, scope: KClass<*>?, generator: KClass<out ObjectIdGenerator<*>>?,
            resolver: KClass<out ObjectIdResolver>) : this(name, scope, generator, false, resolver)

    constructor(name: PropertyName, scope: KClass<*>?, generator: KClass<out ObjectIdGenerator<*>>?,
            alwaysAsId: Boolean) : this(name, scope, generator, alwaysAsId, SimpleObjectIdResolver::class)

    open val propertyName: PropertyName
        get() = myPropertyName

    open val scope: KClass<*>?
        get() = myScope

    open val generatorType: KClass<out ObjectIdGenerator<*>>?
        get() = myGenerator

    open val resolverType: KClass<out ObjectIdResolver>
        get() = myResolver

    open val alwaysAsId: Boolean
        get() = myAlwaysAsId

    open fun withAlwaysAsId(alwaysAsId: Boolean): ObjectIdInfo {
        return ObjectIdInfo(myPropertyName, myScope, myGenerator, alwaysAsId, myResolver)
    }

    override fun toString(): String {
        return "ObjectIdInfo: propName=$myPropertyName, scope=${myScope.name}, generatorType=${myGenerator.name}, alwaysAsId=$alwaysAsId"
    }

    companion object {

        val EMPTY = ObjectIdInfo(PropertyName.NO_NAME, Any::class, null, false, null)

    }

}