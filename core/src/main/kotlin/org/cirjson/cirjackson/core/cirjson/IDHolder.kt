package org.cirjson.cirjackson.core.cirjson

class IDHolder {

    private var currentID = 0

    private val mySavedIds = mutableSetOf<IDEntity>()

    fun setID(id: String, referenced: Any, isArray: Boolean) {
        mySavedIds.add(IDEntity(id, referenced, isArray))
    }

    fun getID(referenced: Any, isArray: Boolean): String {
        val (id, _, isReferencedEntityArray) = mySavedIds.find { referenced === it.referenced } ?: return createID(
                referenced, isArray)

        return if (isArray == isReferencedEntityArray) {
            id
        } else {
            val state = if (isArray) "an array" else "an object"
            val previousState = if (isArray) "an object" else "an array"
            throw IllegalStateException(
                    "The referenced object `$referenced` was previously $previousState and is now $state")
        }
    }

    fun getFromID(id: String): Any? {
        return mySavedIds.find { id == it.id }?.referenced
    }

    fun isReferencedAlreadySaved(referenced: Any): Boolean {
        return mySavedIds.find { it.referenced === referenced } != null
    }

    private fun createID(referenced: Any, isArray: Boolean): String {
        val id = (currentID++).toString()
        val added = IDEntity(id, referenced, isArray)
        mySavedIds.add(added)
        return id
    }

    private data class IDEntity(val id: String, val referenced: Any, val isArray: Boolean)

}