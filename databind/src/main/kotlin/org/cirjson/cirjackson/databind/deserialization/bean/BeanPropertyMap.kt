package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.core.util.InternCache
import org.cirjson.cirjackson.core.util.Named
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.util.IgnorePropertiesUtil
import org.cirjson.cirjackson.databind.util.NameTransformer
import java.util.*

/**
 * Helper class used for storing mapping from property name to [SettableBeanProperty] instances.
 *
 * Note that this class is used instead of generic [HashMap] for a bit of performance gain (and some memory savings):
 * although default implementation is very good for generic use cases, it can be streamlined a bit for specific use case
 * we have. Even relatively small improvements matter since this is directly on the critical path during
 * deserialization, as it is done for each and every POJO property deserialized.
 */
class BeanPropertyMap : Iterable<SettableBeanProperty> {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    /**
     * Array of properties in the exact order they were handed in. This is used by as-array deserialization.
     */
    var myPropertiesInOrder: Array<SettableBeanProperty>

    /**
     * Configuration of alias mappings, if any (`null` if none), aligned with properties in [myPropertiesInOrder].
     */
    private val myAliasDefinitions: Array<Array<PropertyName>?>?

    private val myLocale: Locale

    val isCaseInsensitive: Boolean

    /*
     *******************************************************************************************************************
     * Lookup index information constructed
     *******************************************************************************************************************
     */

    @Transient
    private var myNameMatcher: PropertyNameMatcher? = null

    /**
     * Lazily instantiated array of properties mapped from lookup index, in which first entries are same as in
     * [myPropertiesInOrder] followed by alias mappings.
     */
    @Transient
    private var myPropertiesWithAliases: Array<SettableBeanProperty>? = null

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    /**
     * @param caseInsensitive Whether property name matching should case-insensitive or not
     *
     * @param properties Sequence of primary properties to index
     *
     * @param aliasDefinitions Alias mappings, if any (null if none)
     *
     * @param assignIndexes Whether to assign indices to property entities or not
     */
    internal constructor(properties: Collection<SettableBeanProperty>, aliasDefinitions: Array<Array<PropertyName>?>?,
            locale: Locale, caseInsensitive: Boolean, assignIndexes: Boolean) {
        myPropertiesInOrder = properties.toTypedArray()
        myAliasDefinitions = aliasDefinitions
        myLocale = locale
        isCaseInsensitive = caseInsensitive

        if (assignIndexes) {
            for ((i, property) in myPropertiesInOrder.withIndex()) {
                property.assignIndex(i)
            }
        }
    }

    private constructor(base: BeanPropertyMap, caseInsensitive: Boolean) {
        myPropertiesInOrder = base.myPropertiesInOrder.copyOf()
        myAliasDefinitions = base.myAliasDefinitions
        myLocale = base.myLocale
        isCaseInsensitive = caseInsensitive
    }

    /*
     *******************************************************************************************************************
     * "Mutant factory" methods
     *******************************************************************************************************************
     */

    /**
     * Mutant factory method that constructs a new instance if desired case-insensitivity state differs from the state
     * of this instance; if states are the same, returns `this`.
     */
    fun withCaseInsensitivity(caseInsensitive: Boolean): BeanPropertyMap {
        if (isCaseInsensitive == caseInsensitive) {
            return this
        }

        return BeanPropertyMap(this, caseInsensitive)
    }

    /**
     * Fluent copy method that creates a new instance that is a copy of this instance except for one additional property
     * that is passed as the argument. Note that method does not modify this instance but constructs and returns a new
     * one.
     */
    fun withProperty(newProperty: SettableBeanProperty): BeanPropertyMap {
        val key = newProperty.name

        for (i in myPropertiesInOrder.indices) {
            if (myPropertiesInOrder[i].name == key) {
                myPropertiesInOrder[i] = newProperty
                return this
            }
        }

        val properties = myPropertiesInOrder.toMutableList()
        properties.add(newProperty)
        return BeanPropertyMap(properties, myAliasDefinitions, myLocale, isCaseInsensitive, false)
    }

    /**
     * Mutant factory method for constructing a map where all entries use given prefix
     */
    fun renameAll(context: DeserializationContext, transformer: NameTransformer?): BeanPropertyMap {
        if ((transformer ?: return this) === NameTransformer.NoOpTransformer) {
            return this
        }

        val properties = ArrayList<SettableBeanProperty>(myPropertiesInOrder.size)

        for (originalProperty in myPropertiesInOrder) {
            val property = rename(context, originalProperty, transformer)
            properties.add(property)
        }

        return BeanPropertyMap(properties, myAliasDefinitions, myLocale, isCaseInsensitive, false).initMatcher(
                context.tokenStreamFactory())
    }

    private fun rename(context: DeserializationContext, property: SettableBeanProperty,
            transformer: NameTransformer): SettableBeanProperty {
        val newName = transformer.transform(property.name).let { InternCache.INSTANCE.intern(it) }
        val realProperty = property.withSimpleName(newName)
        val deserializer = realProperty.valueDeserializer ?: return realProperty
        val newDeserializer = deserializer.unwrappingDeserializer(context, transformer)

        return if (newDeserializer !== deserializer) {
            realProperty.withValueDeserializer(newDeserializer)
        } else {
            realProperty
        }
    }

    /**
     * Mutant factory method that will use this instance as the base, and construct an instance that is otherwise same
     * except for excluding properties with specified names, or including only the one marked as included.
     */
    fun withoutProperties(toExclude: Collection<String>?, toInclude: Collection<String>? = null): BeanPropertyMap {
        val realToExclude = if (toExclude.isNullOrEmpty()) {
            toInclude ?: return this
            emptySet()
        } else {
            toExclude
        }

        val properties = ArrayList<SettableBeanProperty>(myPropertiesInOrder.size)

        for (property in myPropertiesInOrder) {
            if (property.name in realToExclude) {
                continue
            }

            if (!IgnorePropertiesUtil.shouldIgnore(property.name, realToExclude, toInclude)) {
                properties.add(property)
            }
        }

        return BeanPropertyMap(properties, myAliasDefinitions, myLocale, isCaseInsensitive, false)
    }

    /**
     * Specialized method that can be used to replace an existing entry (note: entry MUST exist; otherwise exception is
     * thrown) with specified replacement.
     */
    fun replace(oldProperty: SettableBeanProperty, newProperty: SettableBeanProperty) {
        for (i in myPropertiesInOrder.indices) {
            if (myPropertiesInOrder[i] === oldProperty) {
                myPropertiesInOrder[i] = newProperty
                return
            }
        }

        throw NoSuchElementException("No entry '${oldProperty.name}' found, can't replace")
    }

    /**
     * Specialized method for removing specified existing entry (note: entry MUST exist; otherwise exception is thrown).
     */
    fun remove(oldProperty: SettableBeanProperty) {
        val key = oldProperty.name

        val properties = ArrayList<SettableBeanProperty>(myPropertiesInOrder.size)
        var found = false

        for (property in myPropertiesInOrder) {
            if (!found) {
                val match = property.name
                found = match == key

                if (found) {
                    continue
                }
            }

            properties.add(property)
        }

        if (!found) {
            throw NoSuchElementException("No entry '${oldProperty.name}' found, can't remove")
        }

        myPropertiesInOrder = properties.toTypedArray()
    }

    /*
     *******************************************************************************************************************
     * Factory method(s) for helpers
     *******************************************************************************************************************
     */

    fun initMatcher(tokenStreamFactory: TokenStreamFactory): BeanPropertyMap {
        val (names, propertiesWithAliases) = if (myAliasDefinitions == null) {
            myPropertiesInOrder.asList() to myPropertiesInOrder
        } else {
            val allProperties = myPropertiesInOrder.toMutableList()
            val names = ArrayList<Named>(allProperties)

            for ((i, aliases) in myAliasDefinitions.withIndex()) {
                aliases ?: continue
                val primary = myPropertiesInOrder[i]

                for (alias in aliases) {
                    names.add(alias)
                    allProperties.add(primary)
                }
            }

            names to allProperties.toTypedArray()
        }

        myPropertiesWithAliases = propertiesWithAliases

        myNameMatcher = if (isCaseInsensitive) {
            tokenStreamFactory.constructCaseInsensitiveNameMatcher(names, true, myLocale)
        } else {
            tokenStreamFactory.constructNameMatcher(names, true)
        }

        return this
    }

    val nameMatcher: PropertyNameMatcher?
        get() = myNameMatcher

    val nameMatcherProperties: Array<SettableBeanProperty>?
        get() = myPropertiesWithAliases

    /*
     *******************************************************************************************************************
     * Public API, simple accessors
     *******************************************************************************************************************
     */

    val size: Int
        get() = myPropertiesInOrder.size

    fun hasAliases(): Boolean {
        return myAliasDefinitions != null
    }

    /**
     * Accessor for traversing over all contained properties.
     */
    override fun iterator(): Iterator<SettableBeanProperty> {
        return myPropertiesInOrder.asList().iterator()
    }

    /**
     * Accessor that will return initial insertion-ordering of properties contained in this map.
     */
    val primaryProperties: Array<SettableBeanProperty>
        get() = myPropertiesInOrder

    /*
     *******************************************************************************************************************
     * Public API, property definition lookup
     *******************************************************************************************************************
     */

    fun findDefinition(index: Int): SettableBeanProperty? {
        for (property in myPropertiesInOrder) {
            if (property.propertyIndex == index) {
                return property
            }
        }

        return null
    }

    /**
     * NOTE: does NOT do case-insensitive matching. This method should only be used during construction and never during
     * deserialization process nor alias expansion.
     */
    fun findDefinition(key: String): SettableBeanProperty? {
        for (property in myPropertiesInOrder) {
            if (property.name == key) {
                return property
            }
        }

        return null
    }

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */

    override fun toString(): String {
        val stringBuilder = StringBuilder("Properties=[")

        for ((i, property) in myPropertiesInOrder.withIndex()) {
            if (i > 0) {
                stringBuilder.append(", ")
            }

            stringBuilder.append("${property.name}(${property.type})")
        }

        stringBuilder.append("]")

        myAliasDefinitions?.also { stringBuilder.append("(aliases: ${it.size})") }
        return stringBuilder.toString()
    }

    companion object {

        fun construct(properties: Collection<SettableBeanProperty>, aliasDefinitions: Array<Array<PropertyName>?>?,
                locale: Locale, caseInsensitive: Boolean, assignIndexes: Boolean): BeanPropertyMap {
            return BeanPropertyMap(properties, aliasDefinitions, locale, caseInsensitive, assignIndexes)
        }

    }

}