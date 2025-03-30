package org.cirjson.cirjackson.databind.cirjsontype

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.type.WritableTypeID
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.SerializerProvider
import kotlin.reflect.KClass

/**
 * Interface for serializing type information regarding instances of specified base type (super class), so that exact
 * subtype can be properly deserialized later on. These instances are to be called by regular
 * [ValueSerializers][org.cirjson.cirjackson.databind.ValueSerializer] using proper contextual calls, to add type
 * information using mechanism type serializer was configured with.
 */
abstract class TypeSerializer {

    /*
     *******************************************************************************************************************
     * Initialization
     *******************************************************************************************************************
     */

    /**
     * Method called to create contextual version, to be used for values of given property. This may be the type itself
     * (as is the case for bean properties), or values contained (for [Collection] or [Map] valued properties).
     */
    abstract fun forProperty(context: SerializerProvider, property: BeanProperty): TypeSerializer

    /**
     * Accessor for type information inclusion method that serializer uses; indicates how type information is embedded
     * in resulting CirJSON.
     */
    abstract val typeInclusion: CirJsonTypeInfo.As

    /**
     * Name of property that contains type information, if property-based inclusion is used.
     */
    abstract val propertyName: String

    /**
     * Accessor for object that handles conversions between types and matching type ids.
     */
    abstract val typeIdResolver: TypeIdResolver

    /*
     *******************************************************************************************************************
     * Type serialization methods
     *******************************************************************************************************************
     */

    /**
     * Factory method for constructing type id value object to pass to [writeTypePrefix].
     */
    fun typeId(value: Any?, valueShape: CirJsonToken): WritableTypeID {
        val typeId = WritableTypeID(value, valueShape)

        when (typeInclusion) {
            CirJsonTypeInfo.As.EXISTING_PROPERTY -> {
                typeId.inclusion = WritableTypeID.Inclusion.PAYLOAD_PROPERTY
                typeId.asProperty = propertyName
            }

            CirJsonTypeInfo.As.EXTERNAL_PROPERTY -> {
                typeId.inclusion = WritableTypeID.Inclusion.PARENT_PROPERTY
                typeId.asProperty = propertyName
            }

            CirJsonTypeInfo.As.PROPERTY -> {
                typeId.inclusion = WritableTypeID.Inclusion.METADATA_PROPERTY
                typeId.asProperty = propertyName
            }

            CirJsonTypeInfo.As.WRAPPER_ARRAY -> {
                typeId.inclusion = WritableTypeID.Inclusion.WRAPPER_ARRAY
            }

            CirJsonTypeInfo.As.WRAPPER_OBJECT -> {
                typeId.inclusion = WritableTypeID.Inclusion.WRAPPER_OBJECT
            }
        }

        return typeId
    }

    fun typeId(value: Any?, valueShape: CirJsonToken, id: Any): WritableTypeID {
        return typeId(value, valueShape).apply { this.id = id }
    }

    fun typeId(value: Any?, typeForId: KClass<*>, valueShape: CirJsonToken): WritableTypeID {
        return typeId(value, valueShape).apply { forValueType = typeForId }
    }

    /**
     * Method called to write initial part of type information for given value, along with possible wrapping to use:
     * details are specified by `typeID` argument. Note that for structured types (Object, Array), this call will add
     * necessary start token so it should NOT be explicitly written, unlike with non-type-id value writes.
     *
     * @param generator Generator to use for outputting type id and possible wrapping
     *
     * @param context The serialization context
     *
     * @param typeID Details of what type id is to be written, how.
     */
    @Throws(CirJacksonException::class)
    abstract fun writeTypePrefix(generator: CirJsonGenerator, context: SerializerProvider, typeID: WritableTypeID)

    /**
     * Method called to write the "closing" part of type information for given value, along with possible closing
     * wrapping to use: details are specified by `typeId` argument, which should be one returned from an earlier
     * matching call to [writeTypePrefix].
     *
     * @param generator Generator to use for outputting type id and possible wrapping
     *
     * @param context The serialization context
     *
     * @param typeID Details of what type id is to be written, how.
     */
    @Throws(CirJacksonException::class)
    abstract fun writeTypeSuffix(generator: CirJsonGenerator, context: SerializerProvider, typeID: WritableTypeID)

}