package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdGenerators
import org.cirjson.cirjackson.databind.introspection.ObjectIdInfo
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import kotlin.reflect.KClass

open class PropertyBasedObjectIdGenerator protected constructor(scope: KClass<*>,
        protected val myProperty: BeanPropertyWriter) : ObjectIdGenerators.PropertyGenerator(scope) {

    constructor(objectIdInfo: ObjectIdInfo, property: BeanPropertyWriter) : this(objectIdInfo.scope!!, property)

    /**
     * We must override this method, to prevent errors when scopes are the same, but underlying class (on which to
     * access property) is different.
     */
    override fun canUseFor(generator: ObjectIdGenerator<*>): Boolean {
        if (generator !is PropertyBasedObjectIdGenerator || generator::class != this::class) {
            return false
        }

        return generator.scope === scope && generator.myProperty === myProperty
    }

    override fun generateId(forPojo: Any?): Any? {
        try {
            return myProperty.get(forPojo!!)
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Problem accessing property '${myProperty.name}': ${e.message}", e)
        }
    }

    override fun forScope(scope: KClass<*>): ObjectIdGenerator<Any> {
        return if (scope == this.scope) {
            this
        } else {
            PropertyBasedObjectIdGenerator(scope, myProperty)
        }
    }

    override fun newForSerialization(context: Any): ObjectIdGenerator<Any> {
        return this
    }

    override fun key(key: Any?): IDKey? {
        return key?.let { IDKey(this::class, scope, key) }
    }

}