package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.deserialization.BeanDeserializerBuilder
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.util.NameTransformer

open class BeanDeserializer : BeanDeserializerBase {

    /*
     *******************************************************************************************************************
     * Lifecycle, constructors
     *******************************************************************************************************************
     */

    constructor(builder: BeanDeserializerBuilder, beanDescription: BeanDescription, properties: BeanPropertyMap,
            backReferences: Map<String, SettableBeanProperty>?, ignorableProperties: Set<String>?,
            ignoreAllUnknown: Boolean, includableProperties: Set<String>?, hasViews: Boolean) : super(builder,
            beanDescription, properties, backReferences, ignorableProperties, ignoreAllUnknown, includableProperties,
            hasViews)

    /*
     *******************************************************************************************************************
     * Lifecycle, mutant factories
     *******************************************************************************************************************
     */

    override fun unwrappingDeserializer(context: DeserializationContext,
            unwrapper: NameTransformer): ValueDeserializer<Any> {
        TODO("Not yet implemented")
    }

    override fun withObjectIdReader(objectIdReader: ObjectIdReader?): BeanDeserializer {
        TODO("Not yet implemented")
    }

    override fun withByNameInclusion(ignorableProperties: Set<String>?,
            includableProperties: Set<String>?): BeanDeserializer {
        TODO("Not yet implemented")
    }

    override fun withIgnoreAllUnknown(ignoreAllUnknown: Boolean): BeanDeserializerBase {
        TODO("Not yet implemented")
    }

    override fun withBeanProperties(beanProperties: BeanPropertyMap?): BeanDeserializerBase {
        TODO("Not yet implemented")
    }

    override fun asArrayDeserializer(): BeanDeserializerBase {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Lifecycle, initialization
     *******************************************************************************************************************
     */

    override fun initializeNameMatcher(context: DeserializationContext) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Concrete deserialization methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserializeFromObject(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

    @Throws(CirJacksonException::class)
    override fun deserializeUsingPropertyBased(parser: CirJsonParser, context: DeserializationContext): Any? {
        TODO("Not yet implemented")
    }

}