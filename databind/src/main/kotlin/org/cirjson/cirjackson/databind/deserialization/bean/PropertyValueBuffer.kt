package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DatabindException
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.DeserializationFeature
import org.cirjson.cirjackson.databind.deserialization.SettableAnyProperty
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import java.util.*

/**
 * Simple container used for temporarily buffering a set of [PropertyValues][PropertyValue]. Using during construction
 * of beans (and Maps) that use Creators, and hence need buffering before instance (that will have properties to assign
 * values to) is constructed.
 *
 * @property myParametersNeeded Number of creator parameters for which we have not yet received values.
 */
open class PropertyValueBuffer(protected val myParser: CirJsonParser, protected val myContext: DeserializationContext,
        protected var myParametersNeeded: Int, protected val myObjectIdReader: ObjectIdReader?,
        anyParameterSetter: SettableAnyProperty?) {

    /*
     *******************************************************************************************************************
     * Accumulated properties, other stuff
     *******************************************************************************************************************
     */

    /**
     * Buffer used for storing creator parameters for constructing instance.
     */
    protected val myCreatorParameters = arrayOfNulls<Any>(myParametersNeeded)

    /**
     * Bitflag used to track parameters found from incoming data when number of parameters is less than 32 (fits in
     * int).
     */
    protected var myParametersSeen = 0

    /**
     * Bitflag used to track parameters found from incoming data when number of parameters is 32 or higher.
     */
    protected val myParametersSeenBig = if (myParametersNeeded >= 32) BitSet() else null

    /**
     * If we get non-creator parameters before or between creator parameters, those need to be buffered. Buffer is just
     * a simple linked list
     */
    protected var myBuffered: PropertyValue? = null

    /**
     * In case there is an Object ID property to handle, this is the value we have for it.
     */
    protected var myIdValue: Any? = null

    /**
     * "Any setter" property bound to a Creator parameter (via `@CirJsonAnySetter`)
     */
    protected val myAnyParameterSetter = anyParameterSetter?.takeUnless { it.parameterIndex < 0 }

    /**
     * If "Any-setter-via-Creator" exists, we will need to buffer values to feed it, separate from regular, non-creator
     * properties (see [myBuffered]).
     */
    protected var myAnyParameterBuffered: PropertyValue? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Returns `true` if the given property was seen in the CirJSON source by this buffer.
     */
    fun hasParameter(property: SettableBeanProperty): Boolean {
        return (myParametersSeenBig?.get(property.creatorIndex)
                ?: ((myParametersSeen shr property.creatorIndex) and 1)) == 0
    }

    /**
     * A variation of [getParameters] that accepts a single property. Whereas the plural form eagerly fetches and
     * validates all properties, this method may be used (along with [hasParameter]) to let applications only fetch the
     * properties defined in the CirJSON source itself. This also lets them have some other customized behavior for
     * missing properties.
     */
    @Throws(DatabindException::class)
    open fun getParameter(property: SettableBeanProperty): Any? {
        val value = if (hasParameter(property)) {
            myCreatorParameters[property.creatorIndex]
        } else {
            findMissing(property).also { myCreatorParameters[property.creatorIndex] = it }
        }

        if (value == null && myContext.isEnabled(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)) {
            return myContext.reportInputMismatch(property,
                    "Null value for creator property '${property.name}' (index ${property.creatorIndex}); `DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES` enabled")
        }

        return value
    }

    /**
     * Method called to do necessary post-processing such as injection of values and verification of values for required
     * properties, after either [assignParameter] returns `true` (to indicate all creator properties are found), or when
     * then whole CirJSON Object has been processed,
     */
    @Throws(DatabindException::class)
    open fun getParameters(properties: Array<SettableBeanProperty>): Array<Any?> {
        if (myParametersNeeded > 0) {
            if (myParametersSeenBig == null) {
                var mask = myParametersSeen

                for (i in myCreatorParameters.indices) {
                    if (mask and 1 == 0) {
                        myCreatorParameters[i] = findMissing(properties[i])
                    }

                    mask = mask shr 1
                }
            } else {
                val length = myCreatorParameters.size
                var i = 0

                while (myParametersSeenBig.nextClearBit(i).also { i = it } < length) {
                    myCreatorParameters[i] = findMissing(properties[i])
                    ++i
                }
            }
        }

        if (myAnyParameterSetter != null) {
            val anySetterParameterObject = myAnyParameterSetter.createParameterObject()
            var propertyValue = myAnyParameterBuffered

            while (propertyValue != null) {
                propertyValue.value = anySetterParameterObject
                propertyValue = propertyValue.next
            }

            myCreatorParameters[myAnyParameterSetter.parameterIndex] = anySetterParameterObject
        }

        if (myContext.isEnabled(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)) {
            for (i in properties.indices) {
                if (myCreatorParameters[i] != null) {
                    continue
                }

                val property = properties[i]
                return myContext.reportInputMismatch(property,
                        "Null value for creator property '${property.name}' (index ${property.creatorIndex}); `DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES` enabled")
            }
        }

        return myCreatorParameters
    }

    protected open fun findMissing(property: SettableBeanProperty): Any? {
        if ((myAnyParameterSetter?.let { property.creatorIndex == it.parameterIndex } ?: false) &&
                myAnyParameterBuffered != null) {
            return null
        }

        val injectableValueId = property.injectableValueId

        if (injectableValueId != null) {
            return myContext.findInjectableValue(injectableValueId, property, null)
        }

        if (property.isRequired) {
            return myContext.reportInputMismatch(property,
                    "Missing required creator property '${property.name}' (index ${property.creatorIndex})")
        }

        if (myContext.isEnabled(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)) {
            return myContext.reportInputMismatch(property,
                    "Missing creator property '${property.name}' (index ${property.creatorIndex}); `DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES` enabled")
        }

        return try {
            property.nullValueProvider.getAbsentValue(myContext) ?: property.valueDeserializer!!.getAbsentValue(
                    myContext)
        } catch (e: DatabindException) {
            property.member?.also { e.prependPath(it.declaringClass, property.name) }
            throw e
        }
    }

    /*
     *******************************************************************************************************************
     * Other methods
     *******************************************************************************************************************
     */

    /**
     * Helper method called to see if given non-creator property is the "id property"; and if so, handle appropriately.
     */
    @Throws(CirJacksonException::class)
    open fun readIdProperty(propertyName: String): Boolean {
        return (myObjectIdReader != null && propertyName == myObjectIdReader.propertyName.simpleName).also {
            if (it) {
                myIdValue = myObjectIdReader!!.readObjectReference(myParser, myContext)
            }
        }
    }

    /**
     * Helper method called to handle Object ID value collected earlier, if any
     */
    @Throws(CirJacksonException::class)
    open fun handleIdValue(context: DeserializationContext, bean: Any): Any {
        myObjectIdReader ?: return bean

        val idValue = myIdValue

        return if (idValue != null) {
            val readableObjectId = context.findObjectId(idValue, myObjectIdReader.generator, myObjectIdReader.resolver)
            readableObjectId.bindItem(bean)
            myObjectIdReader.idProperty?.setAndReturn(bean, idValue) ?: bean
        } else {
            context.reportUnresolvedObjectId(myObjectIdReader, bean)
        }
    }

    protected open fun buffered(): PropertyValue? {
        return myBuffered
    }

    internal fun bufferedInternal(): PropertyValue? {
        return buffered()
    }

    /**
     * Method called to buffer value for given property, as well as check whether we now have values for all (creator)
     * properties that we expect to get values for.
     *
     * @return `true` if we have received all creator parameters
     */
    open fun assignParameter(property: SettableBeanProperty, value: Any?): Boolean {
        val index = property.creatorIndex
        myCreatorParameters[index] = value

        if (myParametersSeenBig == null) {
            val old = myParametersSeen
            val new = old or (1 shl index)

            if (old != new) {
                myParametersSeen = new

                if (--myParametersNeeded <= 0) {
                    return myObjectIdReader == null || myIdValue != null
                }
            }
        } else if (!myParametersSeenBig[index]) {
            myParametersSeenBig.set(index)
            --myParametersNeeded
        }

        return false
    }

    open fun bufferProperty(property: SettableBeanProperty, value: Any?) {
        myBuffered = PropertyValue.RegularProperty(myBuffered, value, property)
    }

    open fun bufferAnyProperty(property: SettableAnyProperty, propertyName: String, value: Any?) {
        myBuffered = PropertyValue.AnyProperty(myBuffered, value, property, propertyName)
    }

    open fun bufferMapProperty(key: Any?, value: Any?) {
        myBuffered = PropertyValue.MapProperty(myBuffered, value, key)
    }

    open fun bufferAnyParameterProperty(property: SettableAnyProperty, propertyName: String, value: Any?) {
        myBuffered = PropertyValue.AnyParameterProperty(myBuffered, value, property, propertyName)
    }

}