package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DatabindException
import kotlin.reflect.KClass

/**
 * Exception thrown during deserialization when there are object id that can't be resolved.
 */
class UnresolvedForwardReferenceException : DatabindException {

    val readableObjectId: ReadableObjectId?

    val unresolvedIds: List<UnresolvedId>?

    constructor(processor: CirJsonParser?, message: String, location: CirJsonLocation?,
            readableObjectId: ReadableObjectId) : super(processor, message, location) {
        this.readableObjectId = readableObjectId
        unresolvedIds = null
    }

    constructor(processor: CirJsonParser?, message: String) : super(processor, message) {
        this.readableObjectId = null
        unresolvedIds = ArrayList()
    }

    val unresolvedId: Any
        get() = readableObjectId!!.key.key

    fun addUnresolvedId(id: Any, type: KClass<*>, location: CirJsonLocation) {
        (unresolvedIds as ArrayList<UnresolvedId>).add(UnresolvedId(id, type, location))
    }

    override val message: String
        get() {
            val message = super.message

            unresolvedIds ?: return message

            val stringBuilder = StringBuilder(message)

            val iterator = unresolvedIds.iterator()

            while (iterator.hasNext()) {
                val unresolvedId = iterator.next()
                stringBuilder.append(unresolvedId.toString())

                if (iterator.hasNext()) {
                    stringBuilder.append(", ")
                }
            }

            stringBuilder.append('.')
            return stringBuilder.toString()
        }

    /**
     * This method is overridden to prevent filling of the stack trace when constructors are called (unfortunately,
     * alternative constructors cannot be used due to historical reasons). To explicitly fill in stack traces method
     * [withStackTrace] needs to be called after construction.
     */
    override fun fillInStackTrace(): UnresolvedForwardReferenceException {
        return this
    }

    /**
     * "Mutant" factory method for filling in stack trace; needed since the default constructors will not fill in stack
     * trace.
     */
    fun withStackTrace(): UnresolvedForwardReferenceException {
        super.fillInStackTrace()
        return this
    }

}