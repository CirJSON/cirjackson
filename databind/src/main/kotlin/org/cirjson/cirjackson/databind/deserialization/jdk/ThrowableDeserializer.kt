package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.bean.BeanDeserializer
import org.cirjson.cirjackson.databind.deserialization.bean.BeanPropertyMap
import org.cirjson.cirjackson.databind.deserialization.implementation.UnwrappedPropertyHandler
import org.cirjson.cirjackson.databind.util.NameTransformer

/**
 * Deserializer that builds on basic [BeanDeserializer] but override some aspects like instance construction.
 */
open class ThrowableDeserializer : BeanDeserializer {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(source: BeanDeserializer) : super(source) {
        myVanillaProcessing = false
    }

    /**
     * Alternative constructor used when creating "unwrapping" deserializers
     */
    protected constructor(source: BeanDeserializer, unwrapHandler: UnwrappedPropertyHandler?,
            renamedProperties: BeanPropertyMap?, ignoreAllUnknown: Boolean) : super(source, unwrapHandler,
            renamedProperties, ignoreAllUnknown)

    override fun unwrappingDeserializer(context: DeserializationContext,
            unwrapper: NameTransformer): ValueDeserializer<Any> {
        if (this::class != ThrowableDeserializer::class) {
            return this
        }

        val unwrapHandler = myUnwrappedPropertyHandler?.renameAll(context, unwrapper)
        return ThrowableDeserializer(this, unwrapHandler, myBeanProperties!!.renameAll(context, unwrapper), true)
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    @Throws(CirJacksonException::class)
    override fun deserializeFromObject(parser: CirJsonParser, context: DeserializationContext): Any? {
        if (myPropertyBasedCreator != null) {
            return deserializeUsingPropertyBased(parser, context)
        } else if (myDelegateDeserializer != null) {
            return myValueInstantiator.createUsingDelegate(context,
                    myDelegateDeserializer!!.deserialize(parser, context))
        } else if (myBeanType.isAbstract) {
            return context.handleMissingInstantiator(handledType(), valueInstantiator, parser,
                    "abstract type (need to add/enable type information?)")
        }

        val hasStringCreator = myValueInstantiator.canCreateFromString()
        val hasDefaultCreator = myValueInstantiator.canCreateUsingDefault()

        if (!hasStringCreator && !hasDefaultCreator) {
            return context.handleMissingInstantiator(handledType(), valueInstantiator, parser,
                    "Throwable needs a default constructor, a single-String-arg constructor; or explicit @CirJsonCreator")
        }

        var throwable: Throwable? = null
        var pending: Array<Any?>? = null
        var suppressed: Array<Throwable?>? = null
        var pendingIndex = 0
        val propertyNameMatcher = myPropertyNameMatcher!!
        val propertiesByIndex = myPropertiesByIndex!!

        var index = parser.currentNameMatch(propertyNameMatcher)

        while (true) {
            if (index >= 0) {
                parser.nextToken()
                val property = propertiesByIndex[index]

                if (throwable != null) {
                    if ("cause" != property.name || !parser.hasToken(CirJsonToken.VALUE_NULL)) {
                        property.deserializeAndSet(parser, context, throwable)
                    }

                    index = parser.nextNameMatch(propertyNameMatcher)
                    continue
                }

                if (pending == null) {
                    val length = myBeanProperties!!.size
                    pending = arrayOfNulls(length + length)
                } else if (pendingIndex == pending.size) {
                    pending = pending.copyOf(pendingIndex + 10)
                }

                pending[pendingIndex++] = property
                pending[pendingIndex++] = property.deserialize(parser, context)
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            if (index != PropertyNameMatcher.MATCH_UNKNOWN_NAME) {
                if (index == PropertyNameMatcher.MATCH_END_OBJECT) {
                    break
                }

                return handleUnexpectedWithin(parser, context, throwable!!)
            }

            val propertyName = parser.currentName()!!
            parser.nextToken()

            if (PROPERTY_NAME_MESSAGE.equals(propertyName, true)) {
                throwable = instantiate(context, hasStringCreator, parser.valueAsString)

                if (pending != null) {
                    for (i in IntProgression.fromClosedRange(0, pendingIndex, 2)) {
                        val property = pending[i] as SettableBeanProperty
                        property.set(throwable, pending[i + 1])
                    }

                    pending = null
                }

                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            if (myIgnorableProperties?.contains(propertyName) ?: false) {
                parser.skipChildren()
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            if (PROPERTY_NAME_SUPPRESSED.equals(propertyName, true)) {
                suppressed = if (parser.hasToken(CirJsonToken.VALUE_NULL)) {
                    null
                } else {
                    val deserializer =
                            context.findRootValueDeserializer(context.constructType(Array<Throwable>::class)!!)
                    deserializer.deserialize(parser, context) as Array<Throwable?>
                }

                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            if (PROPERTY_NAME_LOCALIZED_MESSAGE.equals(propertyName, true)) {
                parser.skipChildren()
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            myAnySetter?.also { anySetter ->
                if (throwable == null) {
                    throwable = instantiate(context, hasStringCreator, null)
                }

                anySetter.deserializeAndSet(parser, context, throwable, propertyName)
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            if (PROPERTY_NAME_MESSAGE.equals(propertyName, true)) {
                parser.skipChildren()
                index = parser.nextNameMatch(propertyNameMatcher)
                continue
            }

            handleUnknownProperty(parser, context, throwable, propertyName)
            index = parser.nextNameMatch(propertyNameMatcher)
        }

        if (throwable == null) {
            throwable = instantiate(context, hasStringCreator, null)
        }

        if (pending != null) {
            for (i in IntProgression.fromClosedRange(0, pendingIndex, 2)) {
                val property = pending[i] as SettableBeanProperty
                property.set(throwable, pending[i + 1])
            }
        }

        suppressed?.forEach { s ->
            s?.also { throwable.addSuppressed(it) }
        }

        return throwable
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    /**
     * Helper method to initialize Throwable
     */
    private fun instantiate(context: DeserializationContext, hasStringCreator: Boolean,
            valueAsString: String?): Throwable {
        return if (!hasStringCreator) {
            myValueInstantiator.createUsingDefault(context) as Throwable
        } else {
            myValueInstantiator.createFromString(context, valueAsString) as Throwable
        }
    }

    companion object {

        const val PROPERTY_NAME_MESSAGE = "message"

        const val PROPERTY_NAME_SUPPRESSED = "suppressed"

        const val PROPERTY_NAME_LOCALIZED_MESSAGE = "localizedMessage"

        fun construct(source: BeanDeserializer): ThrowableDeserializer {
            return ThrowableDeserializer(source)
        }

    }

}