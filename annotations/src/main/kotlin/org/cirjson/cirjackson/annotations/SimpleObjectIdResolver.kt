package org.cirjson.cirjackson.annotations

/**
 * Simple implementation of [ObjectIdResolver]
 */
open class SimpleObjectIdResolver : ObjectIdResolver {

    protected var myItems: MutableMap<ObjectIdGenerator.IDKey, Any>? = null

    override fun bindItem(id: ObjectIdGenerator.IDKey, pojo: Any) {
        if (myItems == null) {
            myItems = HashMap()
        } else {
            val old = myItems!![id]

            if (old === pojo) {
                return
            }

            throw IllegalStateException("Already had POJO for id (${id.key::class.java.name}) [$id]")
        }

        myItems!![id] = pojo
    }

    override fun resolveId(id: ObjectIdGenerator.IDKey): Any? {
        return myItems?.get(id)
    }

    override fun newForDeserialization(context: Any): ObjectIdResolver {
        return SimpleObjectIdResolver()
    }

    override fun canUseFor(resolver: ObjectIdResolver): Boolean {
        return resolver::class.java === this::class.java
    }

}