package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.io.SerializedString
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.introspection.AnnotatedField
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.serialization.bean.BeanSerializerBase
import org.cirjson.cirjackson.databind.serialization.bean.UnwrappingBeanPropertyWriter
import org.cirjson.cirjackson.databind.serialization.implementation.PropertySerializerMap
import org.cirjson.cirjackson.databind.util.Annotations
import org.cirjson.cirjackson.databind.util.NameTransformer
import org.cirjson.cirjackson.databind.util.className
import org.cirjson.cirjackson.databind.util.name
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.KClass

/**
 * Base bean property handler class, which implements common parts of reflection-based functionality for accessing a
 * property value and serializing it.
 * 
 * Note that current design tries to keep instances immutable (semi-functional style); mostly because these instances
 * are exposed to application code and this is to reduce likelihood of data corruption and synchronization issues.
 */
open class BeanPropertyWriter : PropertyWriter {

    /*
     *******************************************************************************************************************
     * Basic property metadata: name, type, other
     *******************************************************************************************************************
     */

    /**
     * Logical name of the property; will be used as the field name under which value for the property is written.
     */
    protected val myName: SerializedString?

    /**
     * Wrapper name to use for this element, if any
     */
    protected val myWrapperName: PropertyName?

    /**
     * Type property is declared to have, either in class definition or associated annotations.
     */
    protected val myDeclaredType: KotlinType?

    /**
     * Type to use for locating serializer; normally same as return type of the accessor method, but may be overridden
     * by annotations.
     */
    protected val myConfigSerializationType: KotlinType?

    /**
     * Base type of the property, if the declared type is "non-trivial"; meaning it is either a structured type
     * (collection, map, array), or parameterized. Used to retain type information about contained type, which is mostly
     * necessary if type metadata is to be included.
     */
    protected var myNonTrivialBaseType: KotlinType? = null

    /**
     * Annotations from context (most often, class that declares property, or in case of subclass serializer, from that
     * subclass)
     * 
     * NOTE: transient just to support JDK serializability; Annotations do not serialize. At all.
     */
    @Transient
    protected val myContextAnnotations: Annotations?

    /*
     *******************************************************************************************************************
     * Settings for accessing property value to serialize
     *******************************************************************************************************************
     */

    /**
     * Member (field, method) that represents property and allows access to associated annotations.
     */
    protected val myMember: AnnotatedMember?

    /**
     * Accessor method used to get property value, for method-accessible properties. `null` if and only if [myField] is
     * `null`.
     * 
     * `transient` (and `var`) only to support JDK serializability.
     */
    @Transient
    protected var myAccessorMethod: Method?

    /**
     * Field that contains the property value for field-accessible properties. `null` if and only if [myAccessorMethod]
     * is `null`.
     * 
     * `transient` (and `var`) only to support JDK serializability.
     */
    @Transient
    protected var myField: Field?

    /*
     *******************************************************************************************************************
     * Serializers needed
     *******************************************************************************************************************
     */

    /**
     * Serializer to use for writing out the value: `null` if it cannot be known statically; non-`null` if it can.
     */
    protected var mySerializer: ValueSerializer<Any>?

    /**
     * Serializer used for writing out `null` values, if any. If `null`, `null` values are to be suppressed.
     */
    protected var myNullSerializer: ValueSerializer<Any>?

    /**
     * If property being serialized needs type information to be included, this is the type serializer to use. Declared
     * type (possibly augmented with annotations) of property is used for determining exact mechanism to use (compared
     * to actual runtime type used for serializing actual state).
     */
    protected var myTypeSerializer: TypeSerializer?

    /**
     * In case serializer is not known statically (i.e. [mySerializer] is `null`), a lookup structure will be used for
     * storing dynamically resolved mapping from type(s) to serializer(s).
     */
    @Transient
    protected var myDynamicSerializers: PropertySerializerMap?

    /*
     *******************************************************************************************************************
     * Filtering
     *******************************************************************************************************************
     */

    /**
     * Whether `null` values are to be suppressed (nothing written out if value is `null`) or not. Note that this is a
     * configuration value during construction, and actual handling relies on setting (or not) of [myNullSerializer].
     */
    protected val mySuppressNulls: Boolean

    /**
     * Value that is considered default value of the property; used for default-value-suppression if enabled.
     */
    protected val mySuppressableValue: Any?

    /**
     * Alternate set of property writers used when view-based filtering is available for the Bean.
     */
    protected val myIncludeInViews: Array<KClass<*>>?

    /*
     *******************************************************************************************************************
     * Opaque internal data that bean serializer factory and bean serializers can add
     *******************************************************************************************************************
     */

    @Transient
    protected var myInternalSettings: HashMap<Any, Any>? = null

    /*
     *******************************************************************************************************************
     * Construction, configuration
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    protected constructor(propertyDefinition: BeanPropertyDefinition, member: AnnotatedMember?,
            contextAnnotations: Annotations?, declaredType: KotlinType?, serializer: ValueSerializer<*>?,
            typeSerializer: TypeSerializer?, serializationType: KotlinType?, suppressNulls: Boolean,
            suppressableValue: Any?, includeInViews: Array<KClass<*>>?) : super(propertyDefinition) {
        myMember = member
        myContextAnnotations = contextAnnotations

        myName = SerializedString(propertyDefinition.name)
        myWrapperName = propertyDefinition.wrapperName

        myDeclaredType = declaredType
        mySerializer = serializer as ValueSerializer<Any>?
        myDynamicSerializers = if (serializer == null) PropertySerializerMap.emptyForProperties() else null
        myTypeSerializer = typeSerializer
        myConfigSerializationType = serializationType

        if (member is AnnotatedField) {
            myAccessorMethod = null
            myField = member.member as Field
        } else if (member is AnnotatedMethod) {
            myAccessorMethod = member.member
            myField = null
        } else {
            myAccessorMethod = null
            myField = null
        }

        mySuppressNulls = suppressNulls
        mySuppressableValue = suppressableValue

        myNullSerializer = null
        myIncludeInViews = includeInViews
    }

    /**
     * Constructor that may be of use to virtual properties, when there is need for the zero-arg ("default")
     * constructor, and actual initialization is done after constructor call.
     */
    protected constructor() : super(PropertyMetadata.STANDARD_REQUIRED_OR_OPTIONAL) {
        myMember = null
        myContextAnnotations = null

        myName = null
        myWrapperName = null

        myDeclaredType = null
        mySerializer = null
        myDynamicSerializers = null
        myTypeSerializer = null
        myConfigSerializationType = null

        myAccessorMethod = null
        myField = null

        mySuppressNulls = false
        mySuppressableValue = null

        myNullSerializer = null
        myIncludeInViews = null
    }

    /**
     * "Copy constructor" to be used by filtering subclasses
     */
    protected constructor(base: BeanPropertyWriter) : this(base, base.myName)

    protected constructor(base: BeanPropertyWriter, name: PropertyName) : super(base) {
        myMember = base.myMember
        myContextAnnotations = base.myContextAnnotations

        myName = SerializedString(name.simpleName)
        myWrapperName = base.myWrapperName

        myDeclaredType = base.myDeclaredType
        mySerializer = base.mySerializer
        myDynamicSerializers = base.myDynamicSerializers
        myTypeSerializer = base.myTypeSerializer
        myConfigSerializationType = base.myConfigSerializationType

        myAccessorMethod = base.myAccessorMethod
        myField = base.myField

        mySuppressNulls = base.mySuppressNulls
        mySuppressableValue = base.mySuppressableValue

        myNullSerializer = base.myNullSerializer
        myIncludeInViews = base.myIncludeInViews

        myInternalSettings = base.myInternalSettings?.let { HashMap(it) }
        myNonTrivialBaseType = base.myNonTrivialBaseType
    }

    protected constructor(base: BeanPropertyWriter, name: SerializedString?) : super(base) {
        myMember = base.myMember
        myContextAnnotations = base.myContextAnnotations

        myName = name
        myWrapperName = base.myWrapperName

        myDeclaredType = base.myDeclaredType
        mySerializer = base.mySerializer
        myDynamicSerializers = base.myDynamicSerializers
        myTypeSerializer = base.myTypeSerializer
        myConfigSerializationType = base.myConfigSerializationType

        myAccessorMethod = base.myAccessorMethod
        myField = base.myField

        mySuppressNulls = base.mySuppressNulls
        mySuppressableValue = base.mySuppressableValue

        myNullSerializer = base.myNullSerializer
        myIncludeInViews = base.myIncludeInViews

        myInternalSettings = base.myInternalSettings?.let { HashMap(it) }
        myNonTrivialBaseType = base.myNonTrivialBaseType
    }

    open fun rename(transformer: NameTransformer): BeanPropertyWriter {
        val newName = transformer.transform(myName!!.value)

        if (newName == myName.toString()) {
            return this
        }

        return new(PropertyName.construct(newName))
    }

    /**
     * Overridable factory method used by subclasses
     */
    protected open fun new(newName: PropertyName): BeanPropertyWriter {
        if (this::class != BeanPropertyWriter::class) {
            throw IllegalStateException("Method must be overridden by ${this::class}")
        }

        return BeanPropertyWriter(this, newName)
    }

    /**
     * Method called to set, reset, or clear the configured type serializer for property.
     */
    open fun assignTypeSerializer(typeSerializer: TypeSerializer?) {
        myTypeSerializer = typeSerializer
    }

    /**
     * Method called to assign value serializer for property
     */
    open fun assignSerializer(serializer: ValueSerializer<Any>?) {
        if (mySerializer != null && mySerializer !== serializer) {
            throw IllegalStateException(
                    "Cannot override mySerializer: had a ${mySerializer.className}, trying to set to ${serializer.className}")
        }

        mySerializer = serializer
    }

    /**
     * Method called to assign `null` value serializer for property
     */
    open fun assignNullSerializer(serializer: ValueSerializer<Any>?) {
        if (myNullSerializer != null && myNullSerializer !== serializer) {
            throw IllegalStateException(
                    "Cannot override myNullSerializer: had a ${myNullSerializer.className}, trying to set to ${serializer.className}")
        }

        myNullSerializer = serializer
    }

    /**
     * Method called create an instance that handles details of unwrapping contained value.
     */
    open fun unwrappingWriter(unwrapper: NameTransformer): BeanPropertyWriter {
        return UnwrappingBeanPropertyWriter(this, unwrapper)
    }

    /**
     * Method called to define type to consider as "non-trivial" base type, needed for dynamic serialization resolution
     * for complex (usually container) types
     */
    open fun assignNonTrivialBaseType(type: KotlinType?) {
        myNonTrivialBaseType = type
    }

    open fun fixAccess(config: SerializationConfig) {
        myMember!!.fixAccess(config.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
    }

    /*
     *******************************************************************************************************************
     * BeanProperty implementation
     *******************************************************************************************************************
     */

    override val name: String
        get() = myName!!.value

    override val fullName: PropertyName
        get() = PropertyName(myName!!.value)

    override val type: KotlinType
        get() = myDeclaredType!!

    override val wrapperName: PropertyName?
        get() = myWrapperName

    override fun <A : Annotation> getAnnotation(clazz: KClass<A>): A? {
        return myMember?.getAnnotation(clazz)
    }

    override fun <A : Annotation> getContextAnnotation(clazz: KClass<A>): A? {
        return myContextAnnotations?.get(clazz)
    }

    override val member: AnnotatedMember?
        get() = myMember

    /*
     *******************************************************************************************************************
     * Managing and accessing of opaque internal settings (used by extensions)
     *******************************************************************************************************************
     */

    /**
     * Method for accessing value of specified internal setting.
     *
     * @return Value of the setting, if any; `null` if none.
     */
    open fun getInternalSetting(key: Any): Any? {
        return myInternalSettings?.get(key)
    }

    /**
     * Method for setting specific internal setting to given value
     *
     * @return Old value of the setting, if any (`null` if none)
     */
    open fun setInternalSetting(key: Any, value: Any): Any? {
        return (myInternalSettings ?: HashMap<Any, Any>().also { myInternalSettings = it }).put(key, value)
    }

    /**
     * Method for removing entry for specified internal setting.
     *
     * @return Existing value of the setting, if any (`null` if none)
     */
    open fun removeInternalSetting(key: Any): Any? {
        myInternalSettings ?: return null

        val removed = myInternalSettings!!.remove(key)

        if (myInternalSettings!!.isEmpty()) {
            myInternalSettings = null
        }

        return removed
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open val serializedName: SerializableString?
        get() = myName

    open fun hasSerializer(): Boolean {
        return mySerializer != null
    }

    open fun hasNullSerializer(): Boolean {
        return myNullSerializer != null
    }

    open val typeSerializer: TypeSerializer?
        get() = myTypeSerializer

    /**
     * Accessor that will return true if this bean property has to support "unwrapping"; ability to replace POJO 
     * structural wrapping with optional name prefix and/or suffix (or in some cases, just removal of wrapper name).
     * 
     * Default implementation simply returns `false`.
     */
    open val isUnwrapping: Boolean
        get() = false

    open fun willSuppressNulls(): Boolean {
        return mySuppressNulls
    }

    /**
     * Method called to check to see if this property has a name that would conflict with a given name.
     */
    open fun wouldConflictWithName(name: PropertyName): Boolean {
        if (myWrapperName != null) {
            return myWrapperName == name
        }

        return name.hasSimpleName(myName!!.value) && !name.hasNamespace()
    }

    open val serializer: ValueSerializer<Any>?
        get() = mySerializer

    open val mySerializationType: KotlinType?
        get() = myConfigSerializationType

    open val views: Array<KClass<*>>?
        get() = myIncludeInViews

    /*
     *******************************************************************************************************************
     * PropertyWriter methods: serialization
     *******************************************************************************************************************
     */

    /**
     * Method called to access property that this bean stands for, from within given bean, and to serialize it as a
     * CirJSON Object field using appropriate serializer.
     */
    @Throws(Exception::class)
    override fun serializeAsProperty(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
        val v = get(value)

        if (v == null) {
            if (mySuppressableValue != null && provider.includeFilterSuppressNulls(mySuppressableValue)) {
                return
            }

            if (myNullSerializer != null) {
                generator.writeName(myName!!)
                myNullSerializer!!.serializeNullable(null, generator, provider)
            }

            return
        }

        val serializer = if (mySerializer != null) {
            mySerializer!!
        } else {
            val clazz = v::class
            val map = myDynamicSerializers!!
            map.serializerFor(clazz) ?: findAndAddDynamic(map, clazz, provider)
        }

        if (mySuppressableValue != null) {
            if (MARKER_FOR_EMPTY === mySuppressableValue) {
                if (serializer.isEmpty(provider, v)) {
                    return
                }
            } else if (mySuppressableValue == v) {
                return
            }
        }

        if (v === value) {
            if (handleSelfReference(value, generator, provider, serializer)) {
                return
            }
        }

        generator.writeName(myName!!)

        if (myTypeSerializer != null) {
            serializer.serializeWithType(v, generator, provider, myTypeSerializer!!)
        } else {
            serializer.serialize(v, generator, provider)
        }
    }

    /**
     * Method called to indicate that serialization of a field was omitted due to filtering, in cases where backend data
     * format does not allow basic omission.
     */
    @Throws(Exception::class)
    override fun serializeAsOmittedProperty(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
        if (!generator.isAbleOmitProperties) {
            generator.writeOmittedProperty(myName!!.value)
        }
    }

    /**
     * Alternative to [serializeAsProperty] that is used when a POJO is serialized as CirJSON Array (usually when
     * "Shape" is forced as 'Array'): the difference is that no property names are written.
     */
    @Throws(Exception::class)
    override fun serializeAsElement(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
        val v = get(value)

        if (v == null) {
            if (myNullSerializer != null) {
                myNullSerializer!!.serializeNullable(null, generator, provider)
            } else {
                generator.writeNull()
            }

            return
        }

        val serializer = if (mySerializer != null) {
            mySerializer!!
        } else {
            val clazz = v::class
            val map = myDynamicSerializers!!
            map.serializerFor(clazz) ?: findAndAddDynamic(map, clazz, provider)
        }

        if (mySuppressableValue != null) {
            if (MARKER_FOR_EMPTY === mySuppressableValue) {
                if (serializer.isEmpty(provider, v)) {
                    serializeAsOmittedElement(value, generator, provider)
                    return
                }
            } else if (mySuppressableValue == v) {
                serializeAsOmittedElement(value, generator, provider)
                return
            }
        }

        if (v === value) {
            if (handleSelfReference(value, generator, provider, serializer)) {
                return
            }
        }

        if (myTypeSerializer != null) {
            serializer.serializeWithType(v, generator, provider, myTypeSerializer!!)
        } else {
            serializer.serialize(v, generator, provider)
        }
    }

    /**
     * Method called to serialize a placeholder used in tabular output when real value is not to be included (is
     * filtered out), but when we need an entry so that field indexes will not be off. Typically, this should output
     * `null` or empty String, depending on datatype.
     */
    @Throws(Exception::class)
    override fun serializeAsOmittedElement(value: Any, generator: CirJsonGenerator, provider: SerializerProvider) {
        if (myNullSerializer != null) {
            myNullSerializer!!.serializeNullable(null, generator, provider)
        } else {
            generator.writeNull()
        }
    }

    /*
     *******************************************************************************************************************
     * PropertyWriter methods: schema generation
     *******************************************************************************************************************
     */

    override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider) {
        if (isRequired) {
            objectVisitor.property(this)
        } else {
            objectVisitor.optionalProperty(this)
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    protected open fun findAndAddDynamic(map: PropertySerializerMap, rawType: KClass<*>,
            provider: SerializerProvider): ValueSerializer<Any> {
        val type =
                myNonTrivialBaseType?.let { provider.constructSpecializedType(it, rawType) } ?: provider.constructType(
                        rawType)!!
        val result = map.findAndAddPrimarySerializer(type, provider, this)

        if (map !== result.map) {
            myDynamicSerializers = result.map
        }

        return result.serializer
    }

    /**
     * Method that can be used to access value of the property this Object describes, from given bean instance.
     * 
     * Note: method is final as it should not need to be overridden -- rather, calling method(s) ([serializeAsProperty])
     * should be overridden to change the behavior
     */
    fun get(bean: Any): Any? {
        return if (myAccessorMethod != null) myAccessorMethod!!.invoke(bean) else myField!!.get(bean)
    }

    /**
     * Method called to handle a direct self-reference through this property. Method can choose to indicate an error by
     * throwing [DatabindException]; fully handle serialization (and return `true`); or indicate that it should be
     * serialized normally (return `false`).
     * 
     * Default implementation will throw [DatabindException] if [SerializationFeature.FAIL_ON_SELF_REFERENCES] is
     * enabled; or return `false` if it is disabled.
     *
     * @return `true` if method fully handled self-referential value; `false` if not (caller is to handle it) or
     * [DatabindException] if there is no way handle it
     */
    @Throws(CirJacksonException::class)
    protected open fun handleSelfReference(bean: Any, generator: CirJsonGenerator, context: SerializerProvider,
            serializer: ValueSerializer<Any>): Boolean {
        if (serializer.usesObjectId()) {
            return false
        }

        if (context.isEnabled(SerializationFeature.FAIL_ON_SELF_REFERENCES)) {
            if (serializer is BeanSerializerBase) {
                return context.reportBadDefinition(type, "Direct self-reference leading to cycle")
            }

            return false
        }

        if (!context.isEnabled(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL)) {
            return false
        }

        if (myNullSerializer != null) {
            if (!generator.streamWriteContext.isInArray) {
                generator.writeName(myName!!)
            }

            myNullSerializer!!.serializeNullable(null, generator, context)
        }

        return true
    }

    override fun toString(): String {
        val stringBuilder = StringBuilder(40)
        stringBuilder.append("property '").append(name).append("' (")

        if (myAccessorMethod != null) {
            stringBuilder.append("via method ").append(myAccessorMethod!!.declaringClass.kotlin.name).append('#')
                    .append(myAccessorMethod!!.name)
        } else if (myField != null) {
            stringBuilder.append("field \"").append(myField!!.declaringClass.kotlin.name).append('#')
                    .append(myField!!.name).append('"')
        } else {
            stringBuilder.append("virtual")
        }

        if (mySerializer != null) {
            stringBuilder.append(", static serializer of type ").append(mySerializer!!::class.qualifiedName)
        } else {
            stringBuilder.append(", no static serializer")
        }

        stringBuilder.append(')')
        return stringBuilder.toString()
    }

    companion object {

        /**
         * Marker object used to indicate "do not serialize if empty"
         */
        val MARKER_FOR_EMPTY = CirJsonInclude.Include.NON_EMPTY

    }

}