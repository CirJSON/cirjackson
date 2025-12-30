package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

/**
 * Interface that can be implemented by objects that know how to serialize themselves to CirJSON, using
 * [CirJsonGenerator] (and [SerializerProvider] if necessary).
 *
 * Note that implementing this interface binds implementing object closely to CirJackson API, and that it is often not
 * necessary to do so -- if class is a bean, it can be serialized without implementing this interface.
 *
 * Note that while it is possible to just directly implement [CirJacksonSerializable], actual implementations are
 * strongly recommended to instead extend [CirJacksonSerializable.Base].
 */
interface CirJacksonSerializable {

    /**
     * Serialization method called when no additional type information is to be included in serialization.
     */
    @Throws(CirJacksonException::class)
    fun serialize(generator: CirJsonGenerator, context: SerializerProvider)

    /**
     * Serialization method called when additional type information is expected to be included in serialization, for
     * deserialization to use.
     *
     * Usually implementation consists of a call to [TypeSerializer.writeTypePrefix] followed by serialization of
     * contents, followed by a call to [TypeSerializer.writeTypeSuffix]). Details of the type id argument to pass depend
     * on shape of CirJSON Object used (Array, Object or scalar like String/Number/Boolean).
     *
     * Note that some types (most notably, "natural" types: String, Int, Double and Boolean) never include type
     * information.
     */
    @Throws(CirJacksonException::class)
    fun serializeWithType(generator: CirJsonGenerator, context: SerializerProvider, typeSerializer: TypeSerializer)

    /**
     * Base class with minimal implementation, as well as a couple of extension methods that core Jackson databinding
     * makes use of. Use of this base class is strongly recommended over directly implementing [CirJacksonSerializable].
     */
    abstract class Base : CirJacksonSerializable {

        /**
         * Method that may be called on instance to determine if it is considered "empty" for purposes of serialization
         * filtering or not.
         */
        open fun isEmpty(serializers: SerializerProvider): Boolean {
            return false
        }

    }

}