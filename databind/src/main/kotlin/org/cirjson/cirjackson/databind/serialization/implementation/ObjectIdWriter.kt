package org.cirjson.cirjackson.databind.serialization.implementation

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.core.io.SerializedString
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.ValueSerializer

/**
 * Object that knows how to serialize Object Ids.
 *
 * @property propertyName Name of id property to write, if not `null`: if `null`, should only write references, but id
 * property is handled by some other entity.
 *
 * @property generator Blueprint generator instance: actual instance will be fetched from
 * [SerializerProvider][org.cirjson.cirjackson.databind.SerializerProvider] using this as the key.
 *
 * @property serializer Serializer used for serializing id values.
 *
 * @property alwaysAsId Marker that indicates what the first reference is to be serialized as full POJO, or as Object ID
 * (other references will always be serialized as Object ID)
 */
class ObjectIdWriter private constructor(val idType: KotlinType, val propertyName: SerializableString?,
        val generator: ObjectIdGenerator<*>, val serializer: ValueSerializer<Any>?, val alwaysAsId: Boolean) {

    @Suppress("UNCHECKED_CAST")
    fun withSerializer(serializer: ValueSerializer<*>): ObjectIdWriter {
        return ObjectIdWriter(idType, propertyName, generator, serializer as ValueSerializer<Any>, alwaysAsId)
    }

    fun withAlwaysAsId(newState: Boolean): ObjectIdWriter {
        if (newState == alwaysAsId) {
            return this
        }

        return ObjectIdWriter(idType, propertyName, generator, serializer, newState)
    }

    companion object {

        /**
         * Factory method called by [org.cirjson.cirjackson.databind.serialization.bean.BeanSerializerBase] with the
         * initial information based on standard settings for the type for which serializer is being built.
         */
        fun construct(idType: KotlinType, propertyName: PropertyName?, generator: ObjectIdGenerator<*>,
                alwaysAsId: Boolean): ObjectIdWriter {
            val simpleName = propertyName?.simpleName
            val serializableName = simpleName?.let { SerializedString(it) }
            return ObjectIdWriter(idType, serializableName, generator, null, alwaysAsId)
        }

    }

}