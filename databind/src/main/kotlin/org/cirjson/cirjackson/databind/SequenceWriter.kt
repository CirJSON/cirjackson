package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.core.Versioned
import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.databind.configuration.PackageVersion
import org.cirjson.cirjackson.databind.serialization.SerializationContextExtension
import org.cirjson.cirjackson.databind.serialization.implementation.PropertySerializerMap
import org.cirjson.cirjackson.databind.serialization.implementation.TypeWrappedSerializer
import java.io.Closeable
import java.io.Flushable
import java.io.IOException
import kotlin.reflect.KClass

/**
 * Writer class similar to [ObjectWriter], except that it can be used for writing sequences of values, not just a single
 * value. The main use case is in writing very long sequences, or sequences where values are incrementally produced;
 * cases where it would be impractical or at least inconvenient to construct a wrapper container around values (or where
 * no CirJSON array is desired around values).
 * 
 * Differences from [ObjectWriter] include:
 * 
 * * Instances of [SequenceWriter] are stateful, and not thread-safe: f sharing, external synchronization must be used.
 *    
 * * Explicit [close] is needed after all values have been written ([ObjectWriter] can auto-close after individual value
 * writes)
 */
open class SequenceWriter(protected val myProvider: SerializationContextExtension,
        protected val myGenerator: CirJsonGenerator, protected val myCloseGenerator: Boolean,
        prefetch: ObjectWriter.Prefetch) : Versioned, Closeable, Flushable {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    protected val myConfig = myProvider.config

    protected val myRootSerializer = prefetch.valueSerializer

    protected val myTypeSerializer = prefetch.typeSerializer

    protected val myConfigFlush = myConfig.isEnabled(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)

    protected val myConfigCloseCloseable = myConfig.isEnabled(SerializationFeature.CLOSE_CLOSEABLE)

    /*
     *******************************************************************************************************************
     * State
     *******************************************************************************************************************
     */

    /**
     * If [myRootSerializer] is not defined (no root type was used for constructing [ObjectWriter]), we will use simple
     * scheme for keeping track of serializers needed.
     */
    protected var myDynamicSerializers = PropertySerializerMap.emptyForRootValues()

    /**
     * State flag for keeping track of need to write matching `END_ARRAY`, if a `START_ARRAY` was written during
     * initialization
     */
    protected var myOpenArray = false

    protected var myClosed = false

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Internal method called by [ObjectWriter]: should not be called by code outside `cirjackson-databind` classes.
     */
    @Throws(CirJacksonException::class)
    open fun init(wrapInArray: Boolean): SequenceWriter {
        if (wrapInArray) {
            myGenerator.writeStartArray()
            myOpenArray = true
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, basic accessors
     *******************************************************************************************************************
     */

    /**
     * Method that will return version information stored in and read from jar that contains this class.
     */
    override fun version(): Version {
        return PackageVersion.VERSION
    }

    /*
     *******************************************************************************************************************
     * Public API, write operations, related
     *******************************************************************************************************************
     */

    /**
     * Method for writing given value into output, as part of sequence to write. If root type was specified for
     * [ObjectWriter], value must be of compatible type (same or subtype).
     */
    @Throws(CirJacksonException::class)
    open fun write(value: Any?): SequenceWriter {
        if (value == null) {
            myProvider.serializeValue(myGenerator, null)
            return this
        }

        if (myConfigCloseCloseable && value is Closeable) {
            return writeCloseableValue(value)
        }

        var serializer = myRootSerializer

        if (serializer == null) {
            val type = value::class
            serializer = myDynamicSerializers.serializerFor(type) ?: findAndAddDynamic(type)
        }

        myProvider.serializeValue(myGenerator, value, null, serializer)

        if (myConfigFlush) {
            myGenerator.flush()
        }

        return this
    }

    /**
     * Method for writing given value into output, as part of sequence to write; further, full type (often generic, like
     * [Map] is passed in case a new [ValueSerializer] needs to be fetched to handle type.
     *
     * If root type was specified for [ObjectWriter], value must be of compatible type (same or subtype).
     */
    @Throws(CirJacksonException::class)
    open fun write(value: Any?, type: KotlinType): SequenceWriter {
        if (value == null) {
            myProvider.serializeValue(myGenerator, null)
            return this
        }

        if (myConfigCloseCloseable && value is Closeable) {
            return writeCloseableValue(value, type)
        }

        val serializer = myDynamicSerializers.serializerFor(type.rawClass) ?: findAndAddDynamic(type)
        myProvider.serializeValue(myGenerator, value, type, serializer)

        if (myConfigFlush) {
            myGenerator.flush()
        }

        return this
    }

    @Throws(CirJacksonException::class)
    open fun writeAll(value: Array<Any?>): SequenceWriter {
        for (obj in value) {
            write(obj)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    open fun writeAll(iterable: Iterable<*>): SequenceWriter {
        for (value in iterable) {
            write(value)
        }

        return this
    }

    override fun flush() {
        if (!myClosed) {
            myGenerator.flush()
        }
    }

    override fun close() {
        if (myClosed) {
            return
        }

        myClosed = true

        if (myOpenArray) {
            myOpenArray = false
            myGenerator.writeEndArray()
        }

        if (myCloseGenerator) {
            myGenerator.close()
        }
    }

    /*
     *******************************************************************************************************************
     * Internal helper methods, serializer lookups
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun writeCloseableValue(value: Any): SequenceWriter {
        var toClose = value as Closeable?

        try {
            var serializer = myRootSerializer

            if (serializer == null) {
                val type = value::class
                serializer = myDynamicSerializers.serializerFor(type) ?: findAndAddDynamic(type)
            }

            myProvider.serializeValue(myGenerator, value, null, serializer)

            if (myConfigFlush) {
                myGenerator.flush()
            }

            val tempToClose = toClose!!
            toClose = null

            try {
                tempToClose.close()
            } catch (e: IOException) {
                throw CirJacksonIOException.construct(e, myGenerator)
            }
        } finally {
            if (toClose != null) {
                try {
                    toClose.close()
                } catch (_: IOException) {
                }
            }
        }

        return this
    }

    @Throws(CirJacksonException::class)
    protected open fun writeCloseableValue(value: Any, type: KotlinType): SequenceWriter {
        var toClose = value as Closeable?

        try {
            val serializer = myDynamicSerializers.serializerFor(type.rawClass) ?: findAndAddDynamic(type)
            myProvider.serializeValue(myGenerator, value, type, serializer)

            if (myConfigFlush) {
                myGenerator.flush()
            }

            val tempToClose = toClose!!
            toClose = null

            try {
                tempToClose.close()
            } catch (e: IOException) {
                throw CirJacksonIOException.construct(e)
            }
        } finally {
            if (toClose != null) {
                try {
                    toClose.close()
                } catch (_: IOException) {
                }
            }
        }

        return this
    }

    private fun findAndAddDynamic(type: KClass<*>): ValueSerializer<Any> {
        val result = if (myTypeSerializer == null) {
            myDynamicSerializers.findAndAddRootValueSerializer(type, myProvider)
        } else {
            myDynamicSerializers.addSerializer(type,
                    TypeWrappedSerializer(myTypeSerializer, myProvider.findRootValueSerializer(type)))
        }

        myDynamicSerializers = result.map
        return result.serializer
    }

    private fun findAndAddDynamic(type: KotlinType): ValueSerializer<Any> {
        val result = if (myTypeSerializer == null) {
            myDynamicSerializers.findAndAddRootValueSerializer(type, myProvider)
        } else {
            myDynamicSerializers.addSerializer(type,
                    TypeWrappedSerializer(myTypeSerializer, myProvider.findRootValueSerializer(type)))
        }

        myDynamicSerializers = result.map
        return result.serializer
    }

}