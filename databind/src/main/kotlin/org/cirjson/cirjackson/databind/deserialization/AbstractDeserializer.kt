package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdGenerators
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.CirJsonTokenId
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.deserialization.implementation.ObjectIdReader
import org.cirjson.cirjackson.databind.deserialization.implementation.PropertyBasedObjectIdGenerator
import org.cirjson.cirjackson.databind.type.LogicalType
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import org.cirjson.cirjackson.databind.util.name
import kotlin.reflect.KClass

/**
 * Deserializer only used for abstract types used as placeholders during polymorphic type handling deserialization. If
 * so, there is no real deserializer associated with nominal type, just [TypeDeserializer]; and any calls that do not
 * pass such resolver will result in an error.
 */
open class AbstractDeserializer : ValueDeserializer<Any> {

    protected val myBaseType: KotlinType

    protected val myObjectIdReader: ObjectIdReader?

    protected val myBackReferenceProperties: Map<String, SettableBeanProperty>?

    @Transient
    protected val myProperties: Map<String, SettableBeanProperty>?

    protected val myAcceptString: Boolean

    protected val myAcceptBoolean: Boolean

    protected val myAcceptInt: Boolean

    protected val myAcceptDouble: Boolean

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * @param properties Regular properties: currently only needed to support property-annotated Object ID handling with
     * property inclusion (needed for determining type of Object ID to bind)
     */
    constructor(builder: BeanDeserializerBuilder, beanDescription: BeanDescription,
            backReferences: Map<String, SettableBeanProperty>?,
            properties: Map<String, SettableBeanProperty>?) : super() {
        myBaseType = beanDescription.type
        myObjectIdReader = builder.objectIdReader
        myBackReferenceProperties = backReferences
        myProperties = properties
        val rawClass = myBaseType.rawClass
        myAcceptString = rawClass.isAssignableFrom(String::class)
        myAcceptBoolean = rawClass.isAssignableFrom(Boolean::class)
        myAcceptInt = rawClass.isAssignableFrom(Int::class)
        myAcceptDouble = rawClass.isAssignableFrom(Double::class)
    }

    protected constructor(beanDescription: BeanDescription) : super() {
        myBaseType = beanDescription.type
        myObjectIdReader = null
        myBackReferenceProperties = null
        myProperties = null
        val rawClass = myBaseType.rawClass
        myAcceptString = rawClass.isAssignableFrom(String::class)
        myAcceptBoolean = rawClass.isAssignableFrom(Boolean::class)
        myAcceptInt = rawClass.isAssignableFrom(Int::class)
        myAcceptDouble = rawClass.isAssignableFrom(Double::class)
    }

    protected constructor(base: AbstractDeserializer, objectIdReader: ObjectIdReader?,
            properties: Map<String, SettableBeanProperty>?) : super() {
        myBaseType = base.myBaseType
        myObjectIdReader = objectIdReader
        myBackReferenceProperties = base.myBackReferenceProperties
        myProperties = properties
        myAcceptString = base.myAcceptString
        myAcceptBoolean = base.myAcceptBoolean
        myAcceptInt = base.myAcceptInt
        myAcceptDouble = base.myAcceptDouble
    }

    override fun createContextual(context: DeserializationContext, property: BeanProperty?): ValueDeserializer<*> {
        val introspector = context.annotationIntrospector

        if (property == null || introspector == null) {
            return myProperties?.let { AbstractDeserializer(this, myObjectIdReader, null) } ?: this
        }

        val accessor =
                property.member ?: return myProperties?.let { AbstractDeserializer(this, myObjectIdReader, null) }
                        ?: this
        var objectIdInfo = introspector.findObjectIdInfo(context.config, accessor)
                ?: return myProperties?.let { AbstractDeserializer(this, myObjectIdReader, null) } ?: this
        objectIdInfo = introspector.findObjectReferenceInfo(context.config, accessor, objectIdInfo)!!
        val implementationClass = objectIdInfo.generatorType
        var idProperty: SettableBeanProperty? = null

        val (resolver, idInfo) = if (implementationClass == ObjectIdGenerators.PropertyGenerator::class) {
            val propertyName = objectIdInfo.propertyName
            idProperty = myProperties?.get(propertyName.simpleName) ?: return context.reportBadDefinition(myBaseType,
                    "Invalid Object Id definition for ${handledType().name}: cannot find property with name ${propertyName.name()}")
            context.objectIdResolverInstance(accessor,
                    objectIdInfo) to (idProperty.type to PropertyBasedObjectIdGenerator(objectIdInfo.scope!!))
        } else {
            val type = context.constructType(implementationClass)!!
            context.objectIdResolverInstance(accessor, objectIdInfo) to (context.typeFactory.findTypeParameters(type,
                    ObjectIdGenerator::class)[0]!! to context.objectIdGeneratorInstance(accessor, objectIdInfo))
        }

        val (idType, idGenerator) = idInfo
        val deserializer = context.findRootValueDeserializer(idType)
        val objectIdReader =
                ObjectIdReader.construct(idType, objectIdInfo.propertyName, idGenerator, deserializer, idProperty,
                        resolver)
        return AbstractDeserializer(this, objectIdReader, null)
    }

    /*
     *******************************************************************************************************************
     * Public accessors
     *******************************************************************************************************************
     */

    override fun handledType(): KClass<*> {
        return myBaseType.rawClass
    }

    override val isCacheable: Boolean
        get() = true

    override fun logicalType(): LogicalType {
        return LogicalType.POJO
    }

    /**
     * Overridden to return non-`null` for those instances that are handling value for which Object Identity handling is
     * enabled (either via value type or referring property).
     */
    override fun getObjectIdReader(context: DeserializationContext): ObjectIdReader? {
        return myObjectIdReader
    }

    /**
     * Method called by [BeanDeserializer][org.cirjson.cirjackson.databind.deserialization.bean.BeanDeserializer] to
     * resolve back reference part of managed references.
     */
    override fun findBackReference(referenceName: String): SettableBeanProperty? {
        return myBackReferenceProperties?.get(referenceName)
    }

    /*
     *******************************************************************************************************************
     * ValueDeserializer implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun deserializeWithType(parser: CirJsonParser, context: DeserializationContext,
            typeDeserializer: TypeDeserializer): Any? {
        myObjectIdReader ?: return deserializeIfNatural(parser, context) ?: typeDeserializer.deserializeTypedFromObject(
                parser, context)
        var token = parser.currentToken()
        token ?: return deserializeIfNatural(parser, context) ?: typeDeserializer.deserializeTypedFromObject(parser,
                context)

        if (token.isScalarValue) {
            return deserializeFromObjectId(parser, context)
        }

        if (token == CirJsonToken.START_OBJECT) {
            token = parser.nextToken()
        }

        return if (token == CirJsonToken.PROPERTY_NAME && myObjectIdReader.maySerializeAsObject() &&
                myObjectIdReader.isValidReferencePropertyName(parser.currentName()!!, parser)) {
            deserializeFromObjectId(parser, context)
        } else {
            deserializeIfNatural(parser, context) ?: typeDeserializer.deserializeTypedFromObject(parser, context)
        }
    }

    @Throws(CirJacksonException::class)
    override fun deserialize(parser: CirJsonParser, context: DeserializationContext): Any? {
        val bogus = ValueInstantiator.Base(myBaseType)
        return context.handleMissingInstantiator(myBaseType.rawClass, bogus, parser,
                "abstract types either need to be mapped to concrete types, have custom deserializer, or contain additional type information")
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun deserializeIfNatural(parser: CirJsonParser, context: DeserializationContext): Any? {
        return when (parser.currentTokenId()) {
            CirJsonTokenId.ID_STRING -> parser.takeIf { myAcceptString }?.text
            CirJsonTokenId.ID_NUMBER_INT -> parser.takeIf { myAcceptInt }?.intValue
            CirJsonTokenId.ID_NUMBER_FLOAT -> parser.takeIf { myAcceptDouble }?.doubleValue
            CirJsonTokenId.ID_TRUE -> true.takeIf { myAcceptBoolean }
            CirJsonTokenId.ID_FALSE -> false.takeIf { myAcceptBoolean }
            else -> null
        }
    }

    /**
     * Method called in cases where it looks like we got an Object ID to parse and use as a reference.
     */
    @Throws(CirJacksonException::class)
    protected open fun deserializeFromObjectId(parser: CirJsonParser, context: DeserializationContext): Any? {
        val id = myObjectIdReader!!.readObjectReference(parser, context)!!
        val readableObjectId = context.findObjectId(id, myObjectIdReader.generator, myObjectIdReader.resolver)
        return readableObjectId.resolve() ?: throw UnresolvedForwardReferenceException(parser,
                "Could not resolve Object Id [$id] -- unresolved forward-reference?", parser.currentLocation(),
                readableObjectId)
    }

    companion object {

        /**
         * Factory method used when constructing instances for non-POJO types, like [Maps][Map].
         */
        fun constructForNonPOJO(beanDescription: BeanDescription): AbstractDeserializer {
            return AbstractDeserializer(beanDescription)
        }

    }

}