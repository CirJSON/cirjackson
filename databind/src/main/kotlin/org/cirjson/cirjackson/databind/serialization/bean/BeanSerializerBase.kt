package org.cirjson.cirjackson.databind.serialization.bean

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdGenerators
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.type.WritableTypeID
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonFormatVisitorWrapper
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.serialization.*
import org.cirjson.cirjackson.databind.serialization.implementation.ObjectIdWriter
import org.cirjson.cirjackson.databind.serialization.implementation.PropertyBasedObjectIdGenerator
import org.cirjson.cirjackson.databind.serialization.jdk.EnumSerializer
import org.cirjson.cirjackson.databind.serialization.jdk.MapEntrySerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardContainerSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardDelegatingSerializer
import org.cirjson.cirjackson.databind.serialization.standard.StandardSerializer
import org.cirjson.cirjackson.databind.util.*

/**
 * Base class both for the standard bean serializer, and a couple of variants that only differ in small details. Can be
 * used for custom bean serializers as well, although that is not the primary design goal.
 */
abstract class BeanSerializerBase : StandardSerializer<Any> {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    protected val myBeanType: KotlinType

    /**
     * Writers used for outputting actual property values
     */
    protected val myProperties: Array<BeanPropertyWriter>

    /**
     * Optional filters used to suppress output of properties that are only to be included in certain views
     */
    protected val myFilteredProperties: Array<BeanPropertyWriter?>?

    /**
     * Handler for [org.cirjson.cirjackson.annotations.CirJsonAnyGetter] annotated properties
     */
    protected val myAnyGetterWriter: AnyGetterWriter?

    /**
     * ID of the bean property filter to use, if any; `null` if none.
     */
    protected val myPropertyFilterId: Any?

    /**
     * If using custom type ids (usually via getter, or field), this is the reference to that member.
     */
    protected val myTypeId: AnnotatedMember?

    /**
     * If this POJO can be alternatively serialized using just an object id to denote a reference to previously
     * serialized object, this [ObjectIdWriter] will handle details.
     */
    protected val myObjectIdWriter: ObjectIdWriter?

    /**
     * Requested shape from bean class annotations, if any.
     */
    protected val mySerializationShape: CirJsonFormat.Shape?

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Constructor used by [BeanSerializerBuilder] to create an instance
     *
     * @param type Nominal type of values handled by this serializer
     *
     * @param builder Builder for accessing other collected information
     */
    protected constructor(type: KotlinType, builder: BeanSerializerBuilder?, properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?) : super(type) {
        myBeanType = type
        myProperties = properties
        myFilteredProperties = filteredProperties

        if (builder != null) {
            myTypeId = builder.typeId
            myAnyGetterWriter = builder.anyGetter
            myPropertyFilterId = builder.filterId
            myObjectIdWriter = builder.objectIdWriter
            mySerializationShape = builder.beanDescription.findExpectedFormat(type.rawClass)!!.shape
        } else {
            myTypeId = null
            myAnyGetterWriter = null
            myPropertyFilterId = null
            myObjectIdWriter = null
            mySerializationShape = null
        }
    }

    /**
     * Copy-constructor that is useful for subclasses that just want to copy all superclass properties without
     * modifications.
     */
    protected constructor(source: BeanSerializerBase) : this(source, source.myProperties, source.myFilteredProperties)

    protected constructor(source: BeanSerializerBase, properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?) : super(source.myHandledType) {
        myBeanType = source.myBeanType
        myProperties = properties
        myFilteredProperties = filteredProperties
        myTypeId = source.myTypeId
        myAnyGetterWriter = source.myAnyGetterWriter
        myPropertyFilterId = source.myPropertyFilterId
        myObjectIdWriter = source.myObjectIdWriter
        mySerializationShape = source.mySerializationShape
    }

    protected constructor(source: BeanSerializerBase, objectIdWriter: ObjectIdWriter?) : this(source, objectIdWriter,
            source.myPropertyFilterId)

    protected constructor(source: BeanSerializerBase, objectIdWriter: ObjectIdWriter?, filterId: Any?) : super(
            source.myHandledType) {
        myBeanType = source.myBeanType
        myProperties = source.myProperties
        myFilteredProperties = source.myFilteredProperties
        myTypeId = source.myTypeId
        myAnyGetterWriter = source.myAnyGetterWriter
        myPropertyFilterId = filterId
        myObjectIdWriter = objectIdWriter
        mySerializationShape = source.mySerializationShape
    }

    protected constructor(source: BeanSerializerBase, toIgnore: Set<String>?, toInclude: Set<String>?) : super(
            source.myHandledType) {
        myBeanType = source.myBeanType

        val propertiesIn = source.myProperties
        val filteredPropertiesIn = source.myFilteredProperties
        val length = propertiesIn.size

        val propertiesOut = ArrayList<BeanPropertyWriter>(length)
        val filteredPropertiesOut = filteredPropertiesIn?.let { ArrayList<BeanPropertyWriter?>(length) }

        for (i in 0..<length) {
            val beanPropertyWriter = propertiesIn[i]

            if (IgnorePropertiesUtil.shouldIgnore(beanPropertyWriter.name, toIgnore, toInclude)) {
                continue
            }

            propertiesOut.add(beanPropertyWriter)
            filteredPropertiesOut?.add(filteredPropertiesIn[i])
        }

        myProperties = propertiesOut.toTypedArray()
        myFilteredProperties = filteredPropertiesOut?.toTypedArray()
        myTypeId = source.myTypeId
        myAnyGetterWriter = source.myAnyGetterWriter
        myPropertyFilterId = source.myPropertyFilterId
        myObjectIdWriter = source.myObjectIdWriter
        mySerializationShape = source.mySerializationShape
    }

    /**
     * Copy-constructor that will also rename properties with given prefix (if it's non-empty)
     */
    protected constructor(source: BeanSerializerBase, transformer: NameTransformer?) : this(source,
            rename(source.myProperties, transformer), renameNullable(source.myFilteredProperties, transformer))

    /**
     * Mutant factory used for creating a new instance with different [ObjectIdWriter].
     */
    abstract fun withObjectIdWriter(objectIdWriter: ObjectIdWriter?): BeanSerializerBase

    /**
     * Mutant factory used for creating a new instance with additional set of properties to ignore or include (from
     * properties this instance otherwise has)
     */
    protected abstract fun withByNameInclusion(toIgnore: Set<String>?, toInclude: Set<String>?): BeanSerializerBase

    /**
     * Mutant factory for creating a variant that output POJO as a CirJSON Array. Implementations may ignore this
     * request if output as array is not possible (either at all, or reliably).
     */
    protected abstract fun asArraySerializer(): BeanSerializerBase

    /**
     * Mutant factory used for creating a new instance with different filter id (used with `CirJsonFilter` annotation)
     */
    abstract override fun withFilterId(filterId: Any?): BeanSerializerBase

    /**
     * Mutant factory used for creating a new instance with modified set of properties
     */
    protected abstract fun withProperties(properties: Array<BeanPropertyWriter>,
            filteredProperties: Array<BeanPropertyWriter?>?): BeanSerializerBase

    /**
     * Let's force subclasses to implement this, to avoid accidental missing of handling...
     */
    abstract override fun unwrappingSerializer(unwrapper: NameTransformer): BeanSerializerBase

    /*
     *******************************************************************************************************************
     * Post-construction processing: resolvable, contextual
     *******************************************************************************************************************
     */

    /**
     * We need to resolve dependant serializers here to be able to properly handle cyclic type references.
     */
    @Suppress("UNCHECKED_CAST")
    override fun resolve(provider: SerializerProvider) {
        val filteredCount = myFilteredProperties?.size ?: 0

        for (i in myProperties.indices) {
            val property = myProperties[i]

            if (!property.willSuppressNulls() && !property.hasNullSerializer()) {
                val nullSerializer = provider.findNullValueSerializer(property)
                property.assignNullSerializer(nullSerializer)

                if (i < filteredCount) {
                    myFilteredProperties!![i]?.assignNullSerializer(nullSerializer)
                }
            }

            if (property.hasSerializer()) {
                continue
            }

            var serializer = findConvertingSerializer(provider, property)

            if (serializer == null) {
                var type = property.serializationType

                if (type == null) {
                    type = property.type

                    if (!type.isFinal) {
                        if (type.isContainerType || type.containedTypeCount() > 0) {
                            property.assignNonTrivialBaseType(type)
                        }

                        continue
                    }
                }

                serializer = provider.findPrimaryPropertySerializer(type, property)

                if (type.isContainerType) {
                    val typeSerializer = type.contentType!!.typeHandler as TypeSerializer?

                    if (typeSerializer != null) {
                        if (serializer is StandardContainerSerializer<*>) {
                            serializer = serializer.withValueTypeSerializer(typeSerializer) as ValueSerializer<Any>
                        }
                    }
                }
            }

            if (i < filteredCount) {
                val writer = myFilteredProperties!![i]

                if (writer != null) {
                    writer.assignSerializer(serializer)
                    continue
                }
            }

            property.assignSerializer(serializer)
        }

        myAnyGetterWriter?.resolve(provider)
    }

    /**
     * Helper method that can be used to see if specified property is annotated to indicate use of a converter for
     * property value (in case of container types, it is container type itself, not key or content type).
     */
    protected open fun findConvertingSerializer(context: SerializerProvider,
            property: BeanPropertyWriter): ValueSerializer<Any>? {
        val introspector = context.annotationIntrospector ?: return null
        val member = property.member ?: return null
        val converterDefinition = introspector.findSerializationConverter(context.config, member) ?: return null
        val converter = context.converterInstance(member, converterDefinition)!!
        val delegateType = converter.getOutputType(context.typeFactory)
        val serializer = context.takeUnless { delegateType.isJavaLangObject }
                ?.findPrimaryPropertySerializer(delegateType, property)
        return StandardDelegatingSerializer(converter, delegateType, serializer, property)
    }

    override fun createContextual(provider: SerializerProvider, property: BeanProperty?): ValueSerializer<*> {
        val introspector = provider.annotationIntrospector
        val member = property?.takeUnless { introspector == null }?.member
        val config = provider.config

        val format = findFormatOverrides(provider, property, myHandledType)
        var shape: CirJsonFormat.Shape? = null

        if (format.hasShape()) {
            shape = format.shape

            if (shape != CirJsonFormat.Shape.ANY && shape != mySerializationShape) {
                if (myBeanType.isEnumType) {
                    when (shape) {
                        CirJsonFormat.Shape.STRING, CirJsonFormat.Shape.NUMBER, CirJsonFormat.Shape.NUMBER_INT -> {
                            val description = provider.introspectBeanDescription(myBeanType)
                            val serializer = EnumSerializer.construct(myBeanType.rawClass, config, description, format)
                            return provider.handlePrimaryContextualization(serializer, property)!!
                        }

                        else -> {}
                    }
                } else if (shape == CirJsonFormat.Shape.NATURAL) {
                    if (!(myBeanType.isMapLikeType && Map::class.isAssignableFrom(myHandledType)) &&
                            Map.Entry::class.isAssignableFrom(myHandledType)) {
                        val mapEntryType = myBeanType.findSuperType(Map.Entry::class)!!
                        val keyType = mapEntryType.containedTypeOrUnknown(0)
                        val valueType = mapEntryType.containedTypeOrUnknown(1)
                        val serializer = MapEntrySerializer(myBeanType, keyType, valueType, false, null, property)
                        return provider.handlePrimaryContextualization(serializer, property)!!
                    }
                }
            }
        }

        var objectIdWriter = myObjectIdWriter

        var idPropertyOriginIndex = 0
        var ignoredProperties: Set<String>? = null
        var includedProperties: Set<String>? = null
        var newFilterId: Any? = null

        if (member != null) {
            ignoredProperties = introspector!!.findPropertyIgnoralByName(config, member)!!.findIgnoredForSerialization()
            includedProperties = introspector.findPropertyInclusionByName(config, member)!!.included
            var objectIdInfo = introspector.findObjectIdInfo(config, member)

            if (objectIdInfo == null) {
                if (objectIdWriter != null) {
                    objectIdInfo = introspector.findObjectReferenceInfo(config, member, null)

                    if (objectIdInfo != null) {
                        objectIdWriter = myObjectIdWriter.withAlwaysAsId(objectIdInfo.alwaysAsId)
                    }
                }
            } else {
                objectIdInfo = introspector.findObjectReferenceInfo(config, member, objectIdInfo)!!
                val implementationClass = objectIdInfo.generatorType
                val type = provider.constructType(implementationClass)!!
                var idType = provider.typeFactory.findTypeParameters(type, ObjectIdGenerator::class)[0]

                if (implementationClass == ObjectIdGenerators.PropertyGenerator::class) {
                    val propertyName = objectIdInfo.propertyName.simpleName
                    val idProperty: BeanPropertyWriter

                    var i = 0
                    val length = myProperties.size

                    while (true) {
                        if (i == length) {
                            return provider.reportBadDefinition(myBeanType,
                                    "Invalid Object Id definition for ${myBeanType.typeDescription}: cannot find property with name ${propertyName.name()}")
                        }

                        val property = myProperties[i]

                        if (propertyName == property.name) {
                            idProperty = property
                            idPropertyOriginIndex = i
                            break
                        }

                        i++
                    }

                    idType = idProperty.type
                    val generator = PropertyBasedObjectIdGenerator(objectIdInfo, idProperty)
                    objectIdWriter = ObjectIdWriter.construct(idType, null, generator, objectIdInfo.alwaysAsId)
                } else {
                    val generator = provider.objectIdGeneratorInstance(member, objectIdInfo)
                    objectIdWriter = ObjectIdWriter.construct(idType!!, objectIdInfo.propertyName, generator,
                            objectIdInfo.alwaysAsId)
                }
            }

            val filterId = introspector.findFilterId(config, member)

            if (filterId != null && filterId != myPropertyFilterId) {
                newFilterId = filterId
            }
        }

        var contextual = this

        if (idPropertyOriginIndex > 0) {
            val newProperties = Array(myProperties.size) {
                if (it == 0) {
                    myProperties[idPropertyOriginIndex]
                } else if (it <= idPropertyOriginIndex) {
                    myProperties[it - 1]
                } else {
                    myProperties[it]
                }
            }

            val newFilteredProperties = myFilteredProperties?.let {
                Array(it.size) { i ->
                    if (i == 0) {
                        it[idPropertyOriginIndex]
                    } else if (i <= idPropertyOriginIndex) {
                        it[i - 1]
                    } else {
                        it[i]
                    }
                }
            }

            contextual = contextual.withProperties(newProperties, newFilteredProperties)
        }

        if (objectIdWriter != null) {
            val serializer = provider.findRootValueSerializer(objectIdWriter.idType)
            objectIdWriter = objectIdWriter.withSerializer(serializer)

            if (objectIdWriter !== myObjectIdWriter) {
                contextual = contextual.withObjectIdWriter(objectIdWriter)
            }
        }

        if (!ignoredProperties.isNullOrEmpty() || includedProperties != null) {
            contextual = contextual.withByNameInclusion(ignoredProperties, includedProperties)
        }

        if (newFilterId != null) {
            contextual = contextual.withFilterId(newFilterId)
        }

        return if ((shape ?: mySerializationShape) == CirJsonFormat.Shape.ARRAY) {
            contextual.asArraySerializer()
        } else {
            contextual
        }
    }

    /*
     *******************************************************************************************************************
     * Public accessors
     *******************************************************************************************************************
     */

    override fun properties(): Iterator<PropertyWriter> {
        return myProperties.toList<PropertyWriter>().iterator()
    }

    open fun propertyCount(): Int {
        return myProperties.size
    }

    /**
     * Method for checking if view-processing is enabled for this bean, that is, if it has separate set of properties
     * with view-checking added.
     */
    open fun hasViewProperties(): Boolean {
        return myFilteredProperties != null
    }

    open val filterId: Any?
        get() = myPropertyFilterId

    /*
     *******************************************************************************************************************
     * Helper methods for implementation classes
     *******************************************************************************************************************
     */

    /**
     * Helper method for sub-classes to check if it should be possible to construct an "as-array" serializer. Returns `true` if all the following hold true:
     *
     * * have Object ID (it may be allowed in future)
     *
     * * have "any getter"
     */
    open fun canCreateArraySerializer(): Boolean {
        return myObjectIdWriter == null && myAnyGetterWriter == null
    }

    /*
     *******************************************************************************************************************
     * Partial ValueSerializer implementation
     *******************************************************************************************************************
     */

    override fun usesObjectId(): Boolean {
        return myObjectIdWriter != null
    }

    @Throws(CirJacksonException::class)
    override fun serializeWithType(value: Any, generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        if (myObjectIdWriter != null) {
            serializeWithObjectId(value, generator, serializers, typeSerializer)
            return
        }

        val typeIdDefinition = typeIdDefinition(typeSerializer, value, CirJsonToken.START_OBJECT)
        typeSerializer.writeTypePrefix(generator, serializers, typeIdDefinition)
        generator.assignCurrentValue(value)

        if (myPropertyFilterId != null) {
            serializePropertiesFiltered(value, generator, serializers, myPropertyFilterId)
        } else {
            serializeProperties(value, generator, serializers)
        }

        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDefinition)
    }

    @Throws(CirJacksonException::class)
    protected fun serializeWithObjectId(value: Any, generator: CirJsonGenerator, context: SerializerProvider,
            startEndObject: Boolean) {
        val writer = myObjectIdWriter!!
        val objectId = context.findObjectId(value, writer.generator)

        if (objectId.writeAsReference(generator, writer)) {
            return
        }

        val id = objectId.generateId(value)

        if (writer.alwaysAsId) {
            writer.serializer!!.serialize(id!!, generator, context)
            return
        }

        if (startEndObject) {
            generator.writeStartObject(value)
        }

        objectId.writeAsDeclaration(generator)

        if (myPropertyFilterId != null) {
            serializePropertiesFiltered(value, generator, context, myPropertyFilterId)
        } else {
            serializeProperties(value, generator, context)
        }

        if (startEndObject) {
            generator.writeEndObject()
        }
    }

    @Throws(CirJacksonException::class)
    protected fun serializeWithObjectId(value: Any, generator: CirJsonGenerator, context: SerializerProvider,
            typeSerializer: TypeSerializer) {
        generator.assignCurrentValue(value)
        val writer = myObjectIdWriter!!
        val objectId = context.findObjectId(value, writer.generator)

        if (objectId.writeAsReference(generator, writer)) {
            return
        }

        val id = objectId.generateId(value)

        if (writer.alwaysAsId) {
            writer.serializer!!.serialize(id!!, generator, context)
            return
        }

        serializeObjectId(value, generator, context, typeSerializer, objectId)
    }

    @Throws(CirJacksonException::class)
    protected open fun serializeObjectId(value: Any, generator: CirJsonGenerator, context: SerializerProvider,
            typeSerializer: TypeSerializer, objectId: WritableObjectId) {
        val typeIdDefinition = typeIdDefinition(typeSerializer, objectId, CirJsonToken.START_OBJECT)

        typeSerializer.writeTypePrefix(generator, context, typeIdDefinition)
        objectId.writeAsDeclaration(generator)

        if (myPropertyFilterId != null) {
            serializePropertiesFiltered(value, generator, context, myPropertyFilterId)
        } else {
            serializeProperties(value, generator, context)
        }

        typeSerializer.writeTypeSuffix(generator, context, typeIdDefinition)
    }

    protected fun typeIdDefinition(typeSerializer: TypeSerializer, value: Any,
            valueShape: CirJsonToken): WritableTypeID {
        val typeId = (myTypeId ?: return typeSerializer.typeId(value, valueShape)).getValue(value) ?: ""
        return typeSerializer.typeId(value, valueShape, typeId)
    }

    /*
     *******************************************************************************************************************
     * Field serialization methods
     *******************************************************************************************************************
     */

    /**
     * Method called when neither CirJSON Filter is to be applied, nor view-filtering. This means that all property
     * writers are non-`null` and can be called directly.
     */
    @Throws(CirJacksonException::class)
    protected open fun serializePropertiesNoView(value: Any, generator: CirJsonGenerator, context: SerializerProvider,
            properties: Array<BeanPropertyWriter>) {
        var i = 0
        var left = properties.size
        var property: BeanPropertyWriter? = null

        try {
            if (left > 3) {
                do {
                    property = properties[i]
                    property.serializeAsProperty(value, generator, context)
                    property = properties[i + 1]
                    property.serializeAsProperty(value, generator, context)
                    property = properties[i + 2]
                    property.serializeAsProperty(value, generator, context)
                    property = properties[i + 3]
                    property.serializeAsProperty(value, generator, context)
                    left -= 4
                    i += 4
                } while (left > 3)
            }

            if (left == 3) {
                property = properties[i++]
                property.serializeAsProperty(value, generator, context)
            }

            if (left >= 2) {
                property = properties[i++]
                property.serializeAsProperty(value, generator, context)
            }

            if (left >= 1) {
                property = properties[i]
                property.serializeAsProperty(value, generator, context)
            }

            if (myAnyGetterWriter != null) {
                property = null
                myAnyGetterWriter.getAndSerialize(value, generator, context)
            }
        } catch (e: Exception) {
            val name = property?.name ?: "[anySetter]"
            wrapAndThrow(context, e, value, name)
        } catch (e: StackOverflowError) {
            val name = property?.name ?: "[anySetter]"
            throw DatabindException.from(generator, "Infinite recursion (StackOverflowError)", e)
                    .prependPath(value, name)
        }
    }

    /**
     * Method called when no JSON Filter is to be applied, but View filtering is in effect and so some of the properties
     * may be `null` to check.
     */
    @Throws(CirJacksonException::class)
    protected open fun serializePropertiesMaybeView(value: Any, generator: CirJsonGenerator,
            context: SerializerProvider, properties: Array<BeanPropertyWriter?>) {
        var i = 0
        var left = properties.size
        var property: BeanPropertyWriter? = null

        try {
            if (left > 3) {
                do {
                    property = properties[i]?.apply { serializeAsProperty(value, generator, context) }
                    property = properties[i + 1]?.apply { serializeAsProperty(value, generator, context) }
                    property = properties[i + 2]?.apply { serializeAsProperty(value, generator, context) }
                    property = properties[i + 3]?.apply { serializeAsProperty(value, generator, context) }
                    left -= 4
                    i += 4
                } while (left > 3)
            }

            if (left == 3) {
                property = properties[i++]?.apply { serializeAsProperty(value, generator, context) }
            }

            if (left >= 2) {
                property = properties[i++]?.apply { serializeAsProperty(value, generator, context) }
            }

            if (left >= 1) {
                property = properties[i]?.apply { serializeAsProperty(value, generator, context) }
            }

            if (myAnyGetterWriter != null) {
                property = null
                myAnyGetterWriter.getAndSerialize(value, generator, context)
            }
        } catch (e: Exception) {
            val name = property?.name ?: "[anySetter]"
            wrapAndThrow(context, e, value, name)
        } catch (e: StackOverflowError) {
            val name = property?.name ?: "[anySetter]"
            throw DatabindException.from(generator, "Infinite recursion (StackOverflowError)", e)
                    .prependPath(value, name)
        }
    }

    /**
     * Alternative serialization method that gets called when there is a [PropertyFilter] that needs to be called to
     * determine which properties are to be serialized (and possibly how)
     */
    @Throws(CirJacksonException::class)
    protected open fun serializePropertiesFiltered(value: Any, generator: CirJsonGenerator, context: SerializerProvider,
            filterId: Any) {
        val filter = findPropertyFilter(context, filterId, value)
        val properties = if (myFilteredProperties != null && context.activeView != null) {
            myFilteredProperties.also {
                if (filter == null) {
                    serializePropertiesMaybeView(value, generator, context, it)
                    return
                }
            }
        } else {
            myProperties.also {
                if (filter == null) {
                    serializePropertiesNoView(value, generator, context, it)
                    return
                }
            }
        }

        var i = 0

        try {
            while (i < properties.size) {
                properties[i]?.serializeAsProperty(value, generator, context)
                i++
            }

            myAnyGetterWriter?.getAndSerialize(value, generator, context)
        } catch (e: Exception) {
            val name = properties.getOrNull(i)?.name ?: "[anySetter]"
            wrapAndThrow(context, e, value, name)
        } catch (e: StackOverflowError) {
            val name = properties.getOrNull(i)?.name ?: "[anySetter]"
            throw DatabindException.from(generator, "Infinite recursion (StackOverflowError)", e)
                    .prependPath(value, name)
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun serializeProperties(value: Any, generator: CirJsonGenerator, context: SerializerProvider) {
        if (myFilteredProperties != null && context.activeView != null) {
            serializePropertiesMaybeView(value, generator, context, myFilteredProperties)
        } else {
            serializePropertiesNoView(value, generator, context, myProperties)
        }
    }

    /*
     *******************************************************************************************************************
     * Introspection (for schema generation, etc.)
     *******************************************************************************************************************
     */

    override fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType) {
        val objectVisitor = visitor.expectObjectFormat(typeHint) ?: return
        val context = visitor.provider

        if (myPropertyFilterId != null) {
            val filter = findPropertyFilter(context!!, myPropertyFilterId, null)!!

            for (property in myProperties) {
                filter.depositSchemaProperty(property, objectVisitor, context)
            }

            return
        }

        val view = context?.takeUnless { myFilteredProperties == null }?.activeView

        val properties = if (view != null) {
            myFilteredProperties!!
        } else {
            myProperties
        }

        for (property in properties) {
            property?.depositSchemaProperty(objectVisitor, context!!)
        }
    }

    override fun toString(): String {
        return "${this::class.simpleName} for ${handledType()!!.qualifiedName}"
    }

    companion object {

        val NAME_FOR_OBJECT_REFERENCE = PropertyName("#object-ref")

        val NO_PROPS = emptyArray<BeanPropertyWriter>()

        private fun rename(properties: Array<BeanPropertyWriter>,
                transformer: NameTransformer?): Array<BeanPropertyWriter> {
            if (properties.isEmpty() || transformer == null || transformer === NameTransformer.NoOpTransformer) {
                return properties
            }

            val length = properties.size
            return Array(length) { properties[it].rename(transformer) }
        }

        private fun renameNullable(properties: Array<BeanPropertyWriter?>?,
                transformer: NameTransformer?): Array<BeanPropertyWriter?>? {
            if (properties.isNullOrEmpty() || transformer == null || transformer === NameTransformer.NoOpTransformer) {
                return properties
            }

            val length = properties.size
            return Array(length) { properties[it]?.rename(transformer) }
        }

    }

}