package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.deserialization.SettableAnyProperty
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import java.util.*

/**
 * Object that is used to collect arguments for non-default creator (non-default-constructor, or argument-taking factory
 * method) before creator can be called. Since ordering of CirJSON properties is not guaranteed, this may require
 * buffering of values other than ones being passed to creator.
 *
 * @property myValueInstantiator Helper object that knows how to actually construct the instance by invoking creator
 * method with buffered arguments.
 */
class PropertyBasedCreator private constructor(context: DeserializationContext,
        private val myValueInstantiator: ValueInstantiator, creatorProperties: Array<SettableBeanProperty>,
        caseInsensitive: Boolean, addAliases: Boolean) {

    /**
     * Number of properties: usually same as size of [myPropertyLookup], but not necessarily, when we have unnamed
     * injectable properties.
     */
    private val myPropertyCount = creatorProperties.size

    /**
     * Map that contains property objects for either constructor or factory method (whichever one is `null`: one
     * property for each parameter for that one), keyed by logical property name
     */
    private val myPropertyLookup = if (caseInsensitive) {
        CaseInsensitiveMap.construct(context.config.locale)
    } else {
        HashMap()
    }.also {
        if (!addAliases) {
            return@also
        }

        val config = context.config

        for (property in creatorProperties) {
            if (property.isIgnorable) {
                continue
            }

            val aliases = property.findAliases(config)

            if (aliases.isEmpty()) {
                continue
            }

            for (alias in aliases) {
                it[alias.simpleName] = property
            }
        }
    }

    /**
     * Array that contains properties that match creator properties
     */
    private val myPropertiesInOrder = Array(creatorProperties.size) {
        creatorProperties[it].also { property ->
            if (!property.isIgnorable) {
                myPropertyLookup[property.name] = property
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    fun properties(): Collection<SettableBeanProperty> {
        return myPropertyLookup.values
    }

    fun findCreatorProperty(name: String): SettableBeanProperty? {
        return myPropertyLookup[name]
    }

    fun findCreatorProperty(propertyIndex: Int): SettableBeanProperty? {
        for (property in myPropertyLookup.values) {
            if (property.propertyIndex == propertyIndex) {
                return property
            }
        }

        return null
    }

    /*
     *******************************************************************************************************************
     * Building process
     *******************************************************************************************************************
     */

    /**
     * Method called when starting to build a bean instance.
     */
    fun startBuilding(parser: CirJsonParser, context: DeserializationContext,
            objectIdReader: ObjectIdReader?): PropertyValueBuffer {
        return PropertyValueBuffer(parser, context, myPropertyCount, objectIdReader, null)
    }

    /**
     * Method called when starting to build a bean instance.
     */
    fun startBuildingWithAnySetter(parser: CirJsonParser, context: DeserializationContext,
            objectIdReader: ObjectIdReader?, anySetter: SettableAnyProperty?): PropertyValueBuffer {
        return PropertyValueBuffer(parser, context, myPropertyCount, objectIdReader, anySetter)
    }

    @Throws(CirJacksonException::class)
    fun build(context: DeserializationContext, buffer: PropertyValueBuffer): Any? {
        val bean = myValueInstantiator.createFromObjectWith(context, myPropertiesInOrder, buffer)
                ?.let { buffer.handleIdValue(context, it) } ?: return null

        var propertyValue = buffer.bufferedInternal()

        while (propertyValue != null) {
            propertyValue.assign(bean)
            propertyValue = propertyValue.next
        }

        return bean
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * Simple override of standard [HashMap] to support case-insensitive access to creator properties.
     *
     * @property myLocale Lower-casing can have Locale-specific minor variations.
     */
    open class CaseInsensitiveMap(protected val myLocale: Locale) : HashMap<String, SettableBeanProperty>() {

        override operator fun get(key: String): SettableBeanProperty? {
            return super.get(key.lowercase(myLocale))
        }

        override fun put(key: String, value: SettableBeanProperty): SettableBeanProperty? {
            val realKey = key.lowercase(myLocale)
            return super.put(realKey, value)
        }

        companion object {

            fun construct(locale: Locale): CaseInsensitiveMap = CaseInsensitiveMap(locale)

        }

    }

    companion object {

        /**
         * Factory method used for building actual instances to be used with POJOS: resolves deserializers, checks for
         * "null values".
         */
        fun construct(context: DeserializationContext, valueInstantiator: ValueInstantiator,
                sourceCreatorProperties: Array<SettableBeanProperty>,
                allProperties: BeanPropertyMap): PropertyBasedCreator {
            val creatorProperties = Array(sourceCreatorProperties.size) {
                sourceCreatorProperties[it].let { property ->
                    if (!property.hasValueDeserializer() && !property.isInjectionOnly) {
                        property.withValueDeserializer(context.findContextualValueDeserializer(property.type, property))
                    } else {
                        property
                    }
                }
            }

            return PropertyBasedCreator(context, valueInstantiator, creatorProperties, allProperties.isCaseInsensitive,
                    true)
        }

        /**
         * Factory method used for building actual instances to be used with types
         * OTHER than POJOs.
         * resolves deserializers and checks for "null values".
         */
        fun construct(context: DeserializationContext, valueInstantiator: ValueInstantiator,
                sourceCreatorProperties: Array<SettableBeanProperty>, caseInsensitive: Boolean): PropertyBasedCreator {
            val creatorProperties = Array(sourceCreatorProperties.size) {
                sourceCreatorProperties[it].let { property ->
                    if (!property.hasValueDeserializer()) {
                        property.withValueDeserializer(context.findContextualValueDeserializer(property.type, property))
                    } else {
                        property
                    }
                }
            }

            return PropertyBasedCreator(context, valueInstantiator, creatorProperties, caseInsensitive, false)
        }

    }

}