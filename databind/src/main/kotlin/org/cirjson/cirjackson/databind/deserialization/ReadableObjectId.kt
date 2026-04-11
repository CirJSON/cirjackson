package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdResolver
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.emptyIterator
import java.util.*
import kotlin.reflect.KClass

/**
 * Simple value container for containing information about single Object ID during deserialization.
 */
open class ReadableObjectId(protected val myKey: ObjectIdGenerator.IDKey) {

    protected var myItem: Any? = null

    protected var myReferringProperties: LinkedList<Referring>? = null

    protected var myResolver: ObjectIdResolver? = null

    open val key: ObjectIdGenerator.IDKey
        get() = myKey

    open fun appendReferring(currentReferring: Referring) {
        (myReferringProperties ?: LinkedList<Referring>().also { myReferringProperties = it }).add(currentReferring)
    }

    /**
     * Method called to assign actual POJO to which ObjectId refers to: will also handle referring properties, if any,
     * by assigning POJO.
     */
    @Throws(CirJacksonException::class)
    open fun bindItem(item: Any?) {
        myResolver!!.bindItem(myKey, item)
        myItem = item
        val id = myKey.key
        val referringProperties = myReferringProperties ?: return
        val iterator = referringProperties.iterator()
        myReferringProperties = null

        while (iterator.hasNext()) {
            iterator.next().handleResolvedForwardReference(id, item)
        }
    }

    open fun resolve(): Any? {
        return myResolver!!.resolveId(myKey).also { myItem = it }
    }

    open fun hasReferringProperties(): Boolean {
        return !myReferringProperties.isNullOrEmpty()
    }

    open fun referringProperties(): Iterator<Referring> {
        return myReferringProperties?.iterator() ?: emptyIterator()
    }

    /**
     * Method called by [DeserializationContext] at the end of deserialization if this Object ID was not resolved during
     * normal processing. Call is made to allow custom implementations to use alternative resolution strategies;
     * currently the only way to make use of this functionality is by sub-classing [ReadableObjectId] and overriding
     * this method.
     * 
     * Default implementation simply returns `false` to indicate that resolution attempt did not succeed.
     *
     * @return `true`, if resolution succeeded (and no error needs to be reported); `false` to indicate resolution did
     * not succeed.
     */
    open fun tryToResolveUnresolved(context: DeserializationContext): Boolean {
        return false
    }

    /**
     * Allow to access the resolver in case anybody wants to use it directly (for examples from
     * [DeserializationContextExtended.tryToResolveUnresolvedObjectId]), and to set it.
     */
    open var resolver: ObjectIdResolver?
        get() = myResolver
        set(value) {
            myResolver = value
        }

    override fun toString(): String {
        return myKey.toString()
    }


    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    abstract class Referring {

        private val myReference: UnresolvedForwardReferenceException

        private val myBeanType: KClass<*>

        constructor(reference: UnresolvedForwardReferenceException, beanType: KClass<*>) {
            myReference = reference
            myBeanType = beanType
        }

        constructor(reference: UnresolvedForwardReferenceException, beanType: KotlinType) {
            myReference = reference
            myBeanType = beanType.rawClass
        }

        open val location: CirJsonLocation
            get() = myReference.location!!

        open val beanType: KClass<*>
            get() = myBeanType

        @Throws(CirJacksonException::class)
        abstract fun handleResolvedForwardReference(id: Any, value: Any?)

        open fun hasId(id: Any): Boolean {
            return id == myReference.unresolvedId
        }

    }

}