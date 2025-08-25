package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.type.WritableTypeID
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import kotlin.reflect.KClass

/**
 * Base class for all standard CirJackson [TypeSerializers][TypeSerializer].
 */
abstract class TypeSerializerBase protected constructor(protected val myIdResolver: TypeIdResolver,
        protected val myProperty: BeanProperty?) : TypeSerializer() {

    /*
     *******************************************************************************************************************
     * Base implementations, simple accessors
     *******************************************************************************************************************
     */

    override val propertyName: String?
        get() = null

    override val typeIdResolver: TypeIdResolver
        get() = myIdResolver

    @Throws(CirJacksonException::class)
    override fun writeTypePrefix(generator: CirJsonGenerator, context: SerializerProvider,
            typeID: WritableTypeID): WritableTypeID? {
        generateTypeId(context, typeID)

        if (typeID.id == null) {
            return null
        }

        return generator.writeTypePrefix(typeID)
    }

    @Throws(CirJacksonException::class)
    override fun writeTypeSuffix(generator: CirJsonGenerator, context: SerializerProvider,
            typeID: WritableTypeID?): WritableTypeID? {
        typeID ?: return null
        return generator.writeTypeSuffix(typeID)
    }

    /**
     * Helper method that will generate type id to use, if not already passed.
     */
    protected open fun generateTypeId(context: DatabindContext, idMetadata: WritableTypeID) {
        val id = idMetadata.id

        if (id != null) {
            return
        }

        val value = idMetadata.forValue!!
        val typeForId = idMetadata.forValueType
        idMetadata.id = if (typeForId == null) {
            idFromValue(context, value)
        } else {
            idFromValueAndType(context, value, typeForId)
        }
    }

    /*
     *******************************************************************************************************************
     * Helper methods for subclasses
     *******************************************************************************************************************
     */

    protected open fun idFromValue(context: DatabindContext, value: Any): String? {
        val id = myIdResolver.idFromValue(context, value)

        if (id == null) {
            handleMissingId(value)
        }

        return id
    }

    protected open fun idFromValueAndType(context: DatabindContext, value: Any, type: KClass<*>): String? {
        val id = myIdResolver.idFromValueAndType(context, value, type)

        if (id == null) {
            handleMissingId(value)
        }

        return id
    }

    protected open fun handleMissingId(value: Any?) {
        // No-op
    }

}