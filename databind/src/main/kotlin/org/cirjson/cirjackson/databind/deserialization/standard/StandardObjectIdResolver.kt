package org.cirjson.cirjackson.databind.deserialization.standard

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdResolver

open class StandardObjectIdResolver : ObjectIdResolver {

    protected val myIds = HashMap<ObjectIdGenerator.IDKey, Any?>()

    override fun bindItem(id: ObjectIdGenerator.IDKey, pojo: Any?) {
        myIds[id] = pojo
    }

    override fun resolveId(id: ObjectIdGenerator.IDKey): Any? {
        return myIds[id]
    }

    override fun newForDeserialization(context: Any): ObjectIdResolver {
        return this
    }

    override fun canUseFor(resolver: ObjectIdResolver): Boolean {
        return true
    }

    companion object {

        val INSTANCE = StandardObjectIdResolver()

    }

}