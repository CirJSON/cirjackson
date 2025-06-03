package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.SerializableString
import org.cirjson.cirjackson.databind.CirJacksonSerializable
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer

/**
 * Helper class used to encapsulate "raw values", pre-encoded textual content that can be output as opaque value with no
 * quoting/escaping, using [CirJsonGenerator.writeRawValue]. It may be stored in [TokenBuffer], as well as in Tree Model
 * ([org.cirjson.cirjackson.databind.CirJsonNode]).
 */
open class RawValue : CirJacksonSerializable {

    protected var myValue: Any?

    constructor(value: String?) {
        myValue = value
    }

    constructor(value: SerializableString?) {
        myValue = value
    }

    constructor(value: CirJacksonSerializable?) {
        myValue = value
    }

    /**
     * Constructor that may be used by subclasses, and allows passing value types other than ones for which explicit
     * constructor exists. Caller has to take care that values of types not supported by base implementation are handled
     * properly, usually by overriding some of the existing serialization methods.
     */
    protected constructor(value: Any?, bogus: Boolean) {
        myValue = value
    }

    /**
     * Accessor for returning enclosed raw value in whatever form it was created in (usually [String],
     * [SerializableString], or any [CirJacksonSerializable]).
     */
    fun rawValue(): Any? {
        return myValue
    }

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider) {
        if (myValue is CirJacksonSerializable) {
            (myValue as CirJacksonSerializable).serialize(generator, serializers)
        } else {
            serializeImplementation(generator)
        }
    }

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        if (myValue is CirJacksonSerializable) {
            (myValue as CirJacksonSerializable).serialize(generator, serializers, typeSerializer)
        } else {
            serializeImplementation(generator)
        }
    }

    @Throws(CirJacksonException::class)
    fun serialize(generator: CirJsonGenerator) {
        if (myValue is CirJacksonSerializable) {
            generator.writePOJO(myValue)
        } else {
            serializeImplementation(generator)
        }
    }

    @Throws(CirJacksonException::class)
    protected fun serializeImplementation(generator: CirJsonGenerator) {
        if (myValue is SerializableString) {
            generator.writeRawValue(myValue as SerializableString)
        } else {
            generator.writeRawValue(myValue.toString())
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is RawValue) {
            return false
        }

        return myValue != other.myValue
    }

    override fun hashCode(): Int {
        return myValue?.hashCode() ?: 0
    }

    override fun toString(): String {
        return "[RawValue of type ${myValue.className}]"
    }

}