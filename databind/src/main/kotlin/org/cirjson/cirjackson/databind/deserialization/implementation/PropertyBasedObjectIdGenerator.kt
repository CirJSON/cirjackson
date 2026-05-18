package org.cirjson.cirjackson.databind.deserialization.implementation

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdGenerators
import kotlin.reflect.KClass

open class PropertyBasedObjectIdGenerator(scope: KClass<*>) : ObjectIdGenerators.PropertyGenerator(scope) {

    override fun generateId(forPojo: Any?): Any? {
        throw UnsupportedOperationException()
    }

    override fun forScope(scope: KClass<*>): ObjectIdGenerator<Any> {
        return if (scope == this.scope) this else PropertyBasedObjectIdGenerator(scope)
    }

    override fun newForSerialization(context: Any): ObjectIdGenerator<Any> {
        return this
    }

    override fun key(key: Any?): IDKey? {
        key ?: return null
        return IDKey(this::class, scope, key)
    }

}