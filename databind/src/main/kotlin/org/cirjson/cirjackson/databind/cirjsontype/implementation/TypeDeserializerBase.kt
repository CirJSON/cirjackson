package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import org.cirjson.cirjackson.databind.deserialization.standard.NullifyingDeserializer
import org.cirjson.cirjackson.databind.util.isBogusClass
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Base class for all standard CirJackson [TypeDeserializers][TypeDeserializer].
 */
abstract class TypeDeserializerBase : TypeDeserializer {

    protected val myIdResolver: TypeIdResolver

    protected val myBaseType: KotlinType?

    /**
     * Property that contains value for which type information is included; `null` if value is a root value. Note that
     * this value is not assigned during construction but only when [forProperty] is called to create a copy.
     */
    protected val myProperty: BeanProperty?

    /**
     * Type to use as the default implementation, if type id is missing or cannot be resolved.
     */
    protected val myDefaultImplementation: KotlinType?

    /**
     * Name of type property used; needed for non-property versions too, in cases where type id is to be exposed as part
     * of CirJSON.
     */
    protected val myTypePropertyName: String

    protected val myTypeIdVisible: Boolean

    /**
     * For efficient operation we will lazily build mappings from type ids to actual deserializers, once needed.
     */
    protected val myDeserializers: MutableMap<String, ValueDeserializer<Any>>

    protected var myDefaultImplementationDeserializer: ValueDeserializer<Any>? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(baseType: KotlinType?, idResolver: TypeIdResolver, typePropertyName: String?,
            typeIdVisible: Boolean, defaultImplementation: KotlinType?) {
        myBaseType = baseType
        myIdResolver = idResolver
        myTypePropertyName = typePropertyName ?: ""
        myTypeIdVisible = typeIdVisible
        myDeserializers = ConcurrentHashMap(16, 0.75f, 2)
        myDefaultImplementation = defaultImplementation
        myProperty = null
    }

    protected constructor(source: TypeDeserializerBase, property: BeanProperty?) {
        myBaseType = source.myBaseType
        myIdResolver = source.myIdResolver
        myTypePropertyName = source.myTypePropertyName
        myTypeIdVisible = source.myTypeIdVisible
        myDeserializers = source.myDeserializers
        myDefaultImplementation = source.myDefaultImplementation
        myProperty = property
    }

    open fun baseTypeName(): String {
        return myBaseType!!.rawClass.qualifiedName!!
    }

    override val propertyName: String?
        get() = myTypePropertyName

    override val typeIdResolver: TypeIdResolver
        get() = myIdResolver

    override val defaultImplementation: KClass<*>?
        get() = myDefaultImplementation?.rawClass

    override fun hasDefaultImplementation(): Boolean {
        return myDefaultImplementation != null
    }

    open fun baseType(): KotlinType? {
        return myBaseType
    }

    override fun toString(): String {
        return "[${this::class.qualifiedName}; base-type: $myBaseType; id-resolver: $myIdResolver]"
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses
     *******************************************************************************************************************
     */

    protected fun findDeserializer(context: DeserializationContext, typeId: String): ValueDeserializer<Any> {
        var deserializer = myDeserializers[typeId]

        if (deserializer != null) {
            return deserializer
        }

        var type = myIdResolver.typeFromId(context, typeId)

        if (type == null) {
            deserializer = findDefaultImplementationDeserializer(context)

            if (deserializer == null) {
                val actual = handleUnknownTypeId(context, typeId) ?: return NullifyingDeserializer.INSTANCE
                deserializer = context.findContextualValueDeserializer(actual, myProperty)
            }
        } else {
            if (myBaseType != null && myBaseType::class == type::class) {
                if (!type.hasGenericTypes) {
                    try {
                        type = context.constructSpecializedType(myBaseType, type.rawClass)
                    } catch (e: IllegalArgumentException) {
                        throw context.invalidTypeIdException(myBaseType, typeId, e.message!!)
                    }
                }
            }

            deserializer = context.findContextualValueDeserializer(type, myProperty)
        }

        myDeserializers[typeId] = deserializer!!
        return deserializer
    }

    protected fun findDefaultImplementationDeserializer(context: DeserializationContext): ValueDeserializer<Any>? {
        if (myDefaultImplementation == null) {
            if (!context.isEnabled(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE)) {
                return NullifyingDeserializer.INSTANCE
            }

            return null
        }

        val raw = myDefaultImplementation.rawClass

        if (raw.isBogusClass) {
            return NullifyingDeserializer.INSTANCE
        }

        if (myDefaultImplementationDeserializer == null) {
            synchronized(myDefaultImplementation) {
                if (myDefaultImplementationDeserializer == null) {
                    myDefaultImplementationDeserializer =
                            context.findContextualValueDeserializer(myDefaultImplementation, myProperty)
                }
            }
        }

        return myDefaultImplementationDeserializer
    }

    /**
     * Helper method called when [CirJsonParser] indicates that it can use so-called native type ids, and such type id
     * has been found.
     */
    @Throws(CirJacksonException::class)
    protected open fun deserializeWithNativeTypeId(parser: CirJsonParser, context: DeserializationContext,
            typeId: Any?): Any? {
        val deserializer = if (typeId == null) {
            findDefaultImplementationDeserializer(context) ?: return context.reportInputMismatch(baseType(),
                    "No (native) type id found when one was expected for polymorphic type handling")
        } else {
            val typeIdString = typeId as? String ?: typeId.toString()
            findDeserializer(context, typeIdString)
        }

        return deserializer.deserialize(parser, context)
    }

    /**
     * Helper method called when given type id cannot be resolved into concrete deserializer either directly (using
     * given [TypeIdResolver]), or using default type. Default implementation simply throws a [DatabindException] to
     * indicate the problem; subclasses may choose
     *
     * @return If it is possible to resolve type id into a [ValueDeserializer] should return that deserializer;
     * otherwise throw an exception to indicate the problem.
     */
    @Throws(CirJacksonException::class)
    protected open fun handleUnknownTypeId(context: DeserializationContext, typeId: String): KotlinType? {
        var extraDescription = myIdResolver.descriptionForKnownTypeIds

        extraDescription = if (extraDescription == null) {
            "type ids are not statically known"
        } else {
            "known type ids = $extraDescription"
        }

        if (myProperty != null) {
            extraDescription = "$extraDescription (for POJO property '${myProperty.name}')"
        }

        return context.handleUnknownTypeId(myBaseType!!, typeId, myIdResolver, extraDescription)
    }

    @Throws(CirJacksonException::class)
    protected open fun handleMissingTypeId(context: DeserializationContext, extraDescription: String): KotlinType? {
        return context.handleMissingTypeId(myBaseType!!, myIdResolver, extraDescription)
    }

    protected companion object {

        const val ID_NAME = "__cirJsonId__"

    }

}