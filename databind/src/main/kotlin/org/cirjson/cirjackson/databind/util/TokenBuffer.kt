package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.base.ParserMinimalBase
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.exception.StreamWriteException
import org.cirjson.cirjackson.core.exception.UnexpectedEndOfInputException
import org.cirjson.cirjackson.core.extensions.isNotFinite
import org.cirjson.cirjackson.core.io.NumberInput
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.core.util.ByteArrayBuilder
import org.cirjson.cirjackson.core.util.CirJacksonFeatureSet
import org.cirjson.cirjackson.core.util.SimpleStreamWriteContext
import org.cirjson.cirjackson.databind.CirJacksonSerializable
import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.DeserializationFeature
import org.cirjson.cirjackson.databind.configuration.PackageVersion
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.math.BigDecimal
import java.math.BigInteger
import java.util.*
import kotlin.math.min

/**
 * Utility class used for efficient storage of [CirJsonToken] sequences, needed for temporary buffering. Space efficient
 * for different sequence lengths (especially so for smaller ones; but not significantly less efficient for larger),
 * highly efficient for linear iteration and appending. Implemented as a segmented/chunked linked list of tokens; only
 * modifications are via `append`.
 */
open class TokenBuffer : CirJsonGenerator {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    /**
     * Parse context from "parent" parser (one from which content to buffer is read, if specified). Used, if available,
     * when reading content, to present full context as if content was read from the original parser: this is useful in
     * error reporting and sometimes processing as well.
     */
    protected var myParentContext: TokenStreamContext? = null

    /**
     * Bit flag composed of bits that indicate which [StreamWriteFeatures][StreamWriteFeature] are enabled.
     *
     * NOTE: most features have no effect on this class
     */
    protected var myStreamWriteFeatures = DEFAULT_STREAM_WRITE_FEATURES

    protected val myStreamReadConstraints: StreamReadConstraints

    protected var myClosed = false

    protected var myHasNativeTypeIds: Boolean

    protected var myHasNativeObjectIds: Boolean

    protected var myMayHaveNativeIds: Boolean

    /**
     * Flag set during construction, if the use of [BigDecimal] is to be forced on all floating-point values.
     */
    protected var myForceBigDecimal = false

    /*
     *******************************************************************************************************************
     * Token buffering state
     *******************************************************************************************************************
     */

    /**
     * First segment, for contents this buffer has
     */
    protected var myFirst = Segment()

    /**
     * The last segment of this buffer, one that is used for appending more tokens
     */
    protected var myLast = myFirst

    /**
     * Offset within the last segment,
     */
    protected var myAppendAt = 0

    /**
     * If native type ids supported, this is the id for the following value (or first token of one) to be written.
     */
    protected var myTypeId: Any? = null

    /**
     * If native object ids supported, this is the id for the following value (or first token of one) to be written.
     */
    protected var myObjectId: Any? = null

    /**
     * Is there currently a native type or object id buffered?
     */
    protected var myHasNativeId = false

    /*
     *******************************************************************************************************************
     * Output state
     *******************************************************************************************************************
     */

    protected var myTokenWriteContext = SimpleStreamWriteContext.createRootContext(null)

    private var myObjectWriteContext: ObjectWriteContext? = null

    /*
     *******************************************************************************************************************
     * Life-cycle: constructors
     *******************************************************************************************************************
     */

    constructor(writeContext: ObjectWriteContext?, hasNativeIds: Boolean) {
        myObjectWriteContext = writeContext
        myStreamReadConstraints = StreamReadConstraints.defaults()

        myHasNativeTypeIds = hasNativeIds
        myHasNativeObjectIds = hasNativeIds

        myMayHaveNativeIds = hasNativeIds
    }

    protected constructor(parser: CirJsonParser, context: ObjectReadContext?) : super() {
        myParentContext = parser.streamReadContext
        myStreamReadConstraints = context?.streamReadConstraints ?: parser.streamReadConstraints

        myHasNativeTypeIds = parser.isReadingTypeIdPossible
        myHasNativeObjectIds = true
        myMayHaveNativeIds = true

        myForceBigDecimal =
                (context as? DeserializationContext)?.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                        ?: false
    }

    /*
     *******************************************************************************************************************
     * Life-cycle: initialization
     *******************************************************************************************************************
     */

    /**
     * Method that allows explicitly specifying parent parse context to associate with the contents of this buffer.
     * Usually context is assigned at construction, based on given parser; but it is not always available, and may not
     * contain intended context.
     */
    open fun overrideParentContext(context: TokenStreamContext?): TokenBuffer {
        myParentContext = context
        return this
    }

    open fun forceUseOfBigDecimal(boolean: Boolean): TokenBuffer {
        myForceBigDecimal = boolean
        return this
    }

    /*
     *******************************************************************************************************************
     * Parser construction
     *******************************************************************************************************************
     */

    /**
     * Method used to create a [CirJsonParser] that can read contents stored in this buffer. Will create an "empty" read
     * context (see [ObjectReadContext.empty]) which often is not what you want.
     *
     * Note: instances are not synchronized, that is, they are not thread-safe if there are concurrent `append` to the
     * underlying buffer.
     *
     * @return Parser that can be used for reading contents stored in this buffer
     */
    open fun asParser(): CirJsonParser {
        return Parser(ObjectReadContext.empty(), this, myFirst, myHasNativeTypeIds, myHasNativeObjectIds,
                myParentContext, myStreamReadConstraints)
    }

    /**
     * Method used to create a [CirJsonParser] that can read contents stored in this buffer.
     *
     * Note: instances are not synchronized, that is, they are not thread-safe if there are concurrent `append to the
     * underlying buffer.
     *
     * @param readContext Active read context to use.
     *
     * @return Parser that can be used for reading contents stored in this buffer
     */
    open fun asParser(readContext: ObjectReadContext): CirJsonParser {
        return Parser(readContext, this, myFirst, myHasNativeTypeIds, myHasNativeObjectIds, myParentContext,
                myStreamReadConstraints)
    }

    /**
     * Method used to create a [CirJsonParser] that can read contents stored in this buffer.
     *
     * Note: instances are not synchronized, that is, they are not thread-safe if there are concurrent `append` to the
     * underlying buffer.
     *
     * @param readContext Active read context to use.
     *
     * @param baseParser Parser to use for accessing source information like location, configured codec,
     * streamReadConstraints
     *
     * @return Parser that can be used for reading contents stored in this buffer
     */
    open fun asParser(readContext: ObjectReadContext, baseParser: CirJsonParser?): CirJsonParser {
        val parser = Parser(readContext, this, myFirst, myHasNativeTypeIds, myHasNativeObjectIds, myParentContext,
                baseParser?.streamReadConstraints ?: myStreamReadConstraints)

        if (baseParser != null) {
            parser.myLocation = baseParser.currentTokenLocation()
        }

        return parser
    }

    /**
     * Same as:
     * ```
     * val parser = asParser(readContext)
     * p.nextToken()
     * return parser
     * ```
     */
    @Throws(CirJacksonException::class)
    open fun asParserOnFirstToken(readContext: ObjectReadContext): CirJsonParser {
        return asParser(readContext).apply { nextToken() }
    }

    /**
     * Same as:
     * ```
     * val parser = asParser(readContext, baseParser)
     * p.nextToken()
     * return parser
     * ```
     */
    @Throws(CirJacksonException::class)
    open fun asParserOnFirstToken(readContext: ObjectReadContext, baseParser: CirJsonParser?): CirJsonParser {
        return asParser(readContext, baseParser).apply { nextToken() }
    }

    /*
     *******************************************************************************************************************
     * Versioned (mostly since buffer is `CirJsonGenerator`)
     *******************************************************************************************************************
     */

    override fun version(): Version {
        return PackageVersion.VERSION
    }

    /*
     *******************************************************************************************************************
     * Additional accessors
     *******************************************************************************************************************
     */

    fun firstToken(): CirJsonToken? {
        return myFirst.type(0)
    }

    /**
     * Accessor for checking whether this buffer has one or more tokens or not.
     *
     * @return `true` if this buffer instance has no tokens, otherwise `false`
     */
    open fun isEmpty(): Boolean {
        return myAppendAt == 0 && myFirst === myLast
    }

    /*
     *******************************************************************************************************************
     * Other custom methods not needed for implementing interfaces
     *******************************************************************************************************************
     */

    /**
     * Helper method that will append contents of given buffer into this buffer. Not particularly optimized; it can be
     * made faster if there is a need.
     *
     * @return This buffer
     */
    open fun append(other: TokenBuffer): TokenBuffer {
        if (!myHasNativeTypeIds) {
            myHasNativeTypeIds = other.myHasNativeTypeIds
        }

        if (!myHasNativeObjectIds) {
            myHasNativeObjectIds = other.myHasNativeObjectIds
        }

        myMayHaveNativeIds = myHasNativeTypeIds || myHasNativeObjectIds

        val parser = other.asParser()

        while (parser.nextToken() != null) {
            copyCurrentStructure(parser)
        }

        return this
    }

    /**
     * Helper method that will write all contents of this buffer using given [CirJsonGenerator].
     *
     * Note: this method would be enough to implement `ValueSerializer` for `TokenBuffer` type; but we cannot have
     * upwards references (from core to mapper package); and as such we also cannot take second argument.
     */
    @Throws(CirJacksonException::class)
    open fun serialize(generator: CirJsonGenerator) {
        var segment = myFirst
        var pointer = -1

        val checkIds = myMayHaveNativeIds
        var hasIds = checkIds && segment.hasIds()

        while (true) {
            if (++pointer >= Segment.TOKENS_PER_SEGMENT) {
                pointer = 0
                segment = segment.next() ?: break
                hasIds = checkIds && segment.hasIds()
            }

            val token = segment.type(pointer) ?: break

            if (hasIds) {
                var id = segment.findObjectId(pointer)

                if (id != null) {
                    generator.writeObjectId(id)
                }

                id = segment.findTypeId(pointer)

                if (id != null) {
                    generator.writeTypeId(id)
                }
            }

            when (token) {
                CirJsonToken.START_OBJECT -> {
                    generator.writeStartObject()
                }

                CirJsonToken.END_OBJECT -> {
                    generator.writeEndObject()
                }

                CirJsonToken.START_ARRAY -> {
                    generator.writeStartArray()
                }

                CirJsonToken.END_ARRAY -> {
                    generator.writeEndArray()
                }

                CirJsonToken.CIRJSON_ID_PROPERTY_NAME, CirJsonToken.PROPERTY_NAME -> {
                    val obj = segment[pointer]!!

                    if (obj is SerializableString) {
                        generator.writeName(obj)
                    } else {
                        generator.writeName(obj as String)
                    }
                }

                CirJsonToken.VALUE_STRING -> {
                    val obj = segment[pointer]!!

                    if (obj is SerializableString) {
                        generator.writeString(obj)
                    } else {
                        generator.writeString(obj as String)
                    }
                }

                CirJsonToken.VALUE_NUMBER_INT -> {
                    when (val number = segment[pointer]!!) {
                        is Int -> generator.writeNumber(number)
                        is Long -> generator.writeNumber(number)
                        is BigInteger -> generator.writeNumber(number)
                        is Short -> generator.writeNumber(number)
                        else -> generator.writeNumber((number as Number).toInt())
                    }
                }

                CirJsonToken.VALUE_NUMBER_FLOAT -> {
                    when (val number = segment[pointer]) {
                        is Float -> generator.writeNumber(number)
                        is Double -> generator.writeNumber(number)
                        is BigDecimal -> generator.writeNumber(number)
                        null -> generator.writeNull()
                        is String -> generator.writeNumber(number)
                        else -> throw StreamWriteException(generator,
                                "Unrecognized value type for VALUE_NUMBER_FLOAT: ${number::class.qualifiedName}, cannot serialize")
                    }
                }

                CirJsonToken.VALUE_TRUE -> {
                    generator.writeBoolean(true)
                }

                CirJsonToken.VALUE_FALSE -> {
                    generator.writeBoolean(false)
                }

                CirJsonToken.VALUE_NULL -> {
                    generator.writeNull()
                }

                CirJsonToken.VALUE_EMBEDDED_OBJECT -> {
                    when (val value = segment[pointer]) {
                        is RawValue -> value.serialize(generator)
                        is CirJacksonSerializable -> generator.writePOJO(value)
                        else -> generator.writeEmbeddedObject(value)
                    }
                }

                else -> throw RuntimeException("Internal error: should never end up through this code path")
            }
        }
    }

    /**
     * Helper method used by standard deserializer.
     */
    @Throws(CirJacksonException::class)
    open fun deserialize(parser: CirJsonParser, context: DeserializationContext): TokenBuffer {
        if (!parser.hasToken(CirJsonToken.PROPERTY_NAME)) {
            copyCurrentStructure(parser)
            return this
        }

        var token: CirJsonToken?
        writeStartObject()

        do {
            copyCurrentStructure(parser)
        } while (parser.nextToken().also { token = it } == CirJsonToken.PROPERTY_NAME)

        if (token != CirJsonToken.END_OBJECT) {
            return context.reportWrongTokenException(TokenBuffer::class, CirJsonToken.END_OBJECT,
                    "Expected END_OBJECT after copying contents of a JsonParser into TokenBuffer, got $token")
        }

        writeEndObject()
        return this
    }

    override fun toString(): String {
        val MAX_COUNT = 100

        val stringBuilder = StringBuilder()
        stringBuilder.append("[TokenBuffer: ")

        val parser = asParser()
        var count = 0
        val hasNativeIds = myHasNativeTypeIds || myHasNativeObjectIds

        while (true) {
            val token = parser.nextToken() ?: break

            if (count > MAX_COUNT) {
                ++count
                continue
            }

            if (count > 0) {
                stringBuilder.append(", ")
            }

            if (hasNativeIds) {
                appendNativeIds(stringBuilder)
            }

            stringBuilder.append(token)

            if (token == CirJsonToken.PROPERTY_NAME) {
                stringBuilder.append('(')
                stringBuilder.append(parser.currentName)
                stringBuilder.append(')')
            }

            ++count
        }

        if (count >= MAX_COUNT) {
            stringBuilder.append(" ... (truncated ").append(count - MAX_COUNT).append(" entries)")
        }

        stringBuilder.append(']')
        return stringBuilder.toString()
    }

    private fun appendNativeIds(stringBuilder: StringBuilder) {
        val objectId = myLast.findObjectId(myAppendAt - 1)

        if (objectId != null) {
            stringBuilder.append("[objectId=").append(objectId).append(']')
        }

        val typeId = myLast.findTypeId(myAppendAt - 1)

        if (typeId != null) {
            stringBuilder.append("[typeId=").append(typeId).append(']')
        }
    }

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    override val streamWriteContext: TokenStreamContext
        get() = myTokenWriteContext

    override fun currentValue(): Any? {
        return myTokenWriteContext.currentValue()
    }

    override fun assignCurrentValue(value: Any?) {
        myTokenWriteContext.assignCurrentValue(value)
    }

    override val objectWriteContext: ObjectWriteContext?
        get() = myObjectWriteContext

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: configuration
     *******************************************************************************************************************
     */

    override val idName = ID_NAME

    override fun configure(feature: StreamWriteFeature, state: Boolean): CirJsonGenerator {
        myStreamWriteFeatures = if (state) {
            myStreamWriteFeatures or feature.mask
        } else {
            myStreamWriteFeatures and feature.mask.inv()
        }
        return this
    }

    override fun isEnabled(feature: StreamWriteFeature): Boolean {
        return myStreamWriteFeatures and feature.mask != 0
    }

    override val streamWriteFeatures: Int
        get() = myStreamWriteFeatures

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: capability introspection
     *******************************************************************************************************************
     */

    override val streamWriteCapabilities: CirJacksonFeatureSet<StreamWriteCapability>
        get() = BOGUS_WRITE_CAPABILITIES

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: low-level output handling
     *******************************************************************************************************************
     */

    override fun flush() {
        // NO OP
    }

    override fun close() {
        myClosed = true
    }

    override val isClosed: Boolean
        get() = myClosed

    override val streamWriteOutputTarget: Any?
        get() = null

    override val streamWriteOutputBuffered: Int
        get() = -1

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: write methods, structural
     *******************************************************************************************************************
     */

    override fun getID(target: Any, isArray: Boolean): String {
        throw UnsupportedOperationException("Called operation not supported for TokenBuffer")
    }

    final override fun writeStartArray(): CirJsonGenerator {
        appendStartMarker(CirJsonToken.START_ARRAY)
        myTokenWriteContext = myTokenWriteContext.createChildArrayContext(null)
        return this
    }

    final override fun writeStartArray(currentValue: Any?): CirJsonGenerator {
        appendStartMarker(CirJsonToken.START_ARRAY)
        myTokenWriteContext = myTokenWriteContext.createChildArrayContext(currentValue)
        return this
    }

    final override fun writeStartArray(currentValue: Any?, size: Int): CirJsonGenerator {
        appendStartMarker(CirJsonToken.START_ARRAY)
        myTokenWriteContext = myTokenWriteContext.createChildArrayContext(currentValue)
        return this
    }

    final override fun writeEndArray(): CirJsonGenerator {
        appendEndMarker(CirJsonToken.END_ARRAY)
        return this
    }

    final override fun writeStartObject(): CirJsonGenerator {
        appendStartMarker(CirJsonToken.START_OBJECT)
        myTokenWriteContext = myTokenWriteContext.createChildObjectContext(null)
        return this
    }

    final override fun writeStartObject(currentValue: Any?): CirJsonGenerator {
        appendStartMarker(CirJsonToken.START_OBJECT)
        myTokenWriteContext = myTokenWriteContext.createChildObjectContext(currentValue)
        return this
    }

    final override fun writeStartObject(currentValue: Any?, size: Int): CirJsonGenerator {
        appendStartMarker(CirJsonToken.START_OBJECT)
        myTokenWriteContext = myTokenWriteContext.createChildObjectContext(currentValue)
        return this
    }

    final override fun writeEndObject(): CirJsonGenerator {
        appendEndMarker(CirJsonToken.END_OBJECT)
        return this
    }

    override fun writeName(name: String): CirJsonGenerator {
        myTokenWriteContext.writeName(name)
        appendName(name)
        return this
    }

    override fun writeName(name: SerializableString): CirJsonGenerator {
        myTokenWriteContext.writeName(name.value)
        appendName(name)
        return this
    }

    override fun writePropertyId(id: Long): CirJsonGenerator {
        val name = id.toString()
        myTokenWriteContext.writeName(name)
        appendName(name)
        return this
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: write methods, textual
     *******************************************************************************************************************
     */

    override fun writeString(value: String?): CirJsonGenerator {
        value ?: return writeNull()
        appendValue(CirJsonToken.VALUE_STRING, value)
        return this
    }

    override fun writeString(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        return writeString(String(buffer, offset, length))
    }

    override fun writeString(value: SerializableString): CirJsonGenerator {
        appendValue(CirJsonToken.VALUE_STRING, value)
        return this
    }

    override fun writeString(reader: Reader?, length: Int): CirJsonGenerator {
        reader ?: return reportError("null reader")
        var toRead = if (length >= 0) length else Int.MAX_VALUE

        val buffer = CharArray(1000)
        val stringBuilder = StringBuilder(1000)

        while (toRead > 0) {
            val toReadNow = min(toRead, buffer.size)

            val numberRead = try {
                reader.read(buffer, 0, toReadNow)
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }

            if (numberRead <= 0) {
                break
            }

            stringBuilder.appendRange(buffer, 0, numberRead)
            toRead -= numberRead
        }

        if (toRead > 0 && length >= 0) {
            return reportError("Was not able to read enough from reader")
        }

        appendValue(CirJsonToken.VALUE_STRING, stringBuilder.toString())
        return this
    }

    override fun writeRawUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRaw(text: String): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRaw(text: String, offset: Int, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRaw(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRaw(char: Char): CirJsonGenerator {
        return reportUnsupportedOperation()
    }

    override fun writeRawValue(text: String): CirJsonGenerator {
        appendValue(CirJsonToken.VALUE_EMBEDDED_OBJECT, RawValue(text))
        return this
    }

    override fun writeRawValue(text: String, offset: Int, length: Int): CirJsonGenerator {
        val realText = text.takeUnless { offset > 0 || length != it.length } ?: text.substring(offset, offset + length)
        appendValue(CirJsonToken.VALUE_EMBEDDED_OBJECT, RawValue(realText))
        return this
    }

    override fun writeRawValue(text: CharArray, offset: Int, length: Int): CirJsonGenerator {
        appendValue(CirJsonToken.VALUE_EMBEDDED_OBJECT, String(text, offset, length))
        return this
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: write methods, primitive types
     *******************************************************************************************************************
     */

    override fun writeNumber(value: Short): CirJsonGenerator {
        appendValue(CirJsonToken.VALUE_NUMBER_INT, value)
        return this
    }

    override fun writeNumber(value: Int): CirJsonGenerator {
        appendValue(CirJsonToken.VALUE_NUMBER_INT, value)
        return this
    }

    override fun writeNumber(value: Long): CirJsonGenerator {
        appendValue(CirJsonToken.VALUE_NUMBER_INT, value)
        return this
    }

    override fun writeNumber(value: BigInteger?): CirJsonGenerator {
        value ?: return writeNull()
        appendValue(CirJsonToken.VALUE_NUMBER_INT, value)
        return this
    }

    override fun writeNumber(value: Float): CirJsonGenerator {
        appendValue(CirJsonToken.VALUE_NUMBER_FLOAT, value)
        return this
    }

    override fun writeNumber(value: Double): CirJsonGenerator {
        appendValue(CirJsonToken.VALUE_NUMBER_FLOAT, value)
        return this
    }

    override fun writeNumber(value: BigDecimal?): CirJsonGenerator {
        value ?: return writeNull()
        appendValue(CirJsonToken.VALUE_NUMBER_FLOAT, value)
        return this
    }

    override fun writeNumber(encodedValue: String?): CirJsonGenerator {
        encodedValue ?: return writeNull()
        appendValue(CirJsonToken.VALUE_NUMBER_FLOAT, encodedValue)
        return this
    }

    private fun writeLazyInteger(encodedValue: Any) {
        appendValue(CirJsonToken.VALUE_NUMBER_INT, encodedValue)
    }

    private fun writeLazyDecimal(encodedValue: Any) {
        appendValue(CirJsonToken.VALUE_NUMBER_FLOAT, encodedValue)
    }

    override fun writeBoolean(state: Boolean): CirJsonGenerator {
        appendValue(if (state) CirJsonToken.VALUE_TRUE else CirJsonToken.VALUE_FALSE)
        return this
    }

    override fun writeNull(): CirJsonGenerator {
        appendValue(CirJsonToken.VALUE_NULL)
        return this
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: write methods, POJOs/trees
     *******************************************************************************************************************
     */

    override fun writePOJO(pojo: Any?): CirJsonGenerator {
        pojo ?: return writeNull()
        val raw = pojo::class

        if (raw == ByteArray::class || pojo is RawValue || myObjectWriteContext == null) {
            appendValue(CirJsonToken.VALUE_EMBEDDED_OBJECT, pojo)
            return this
        }

        myObjectWriteContext!!.writeValue(this, pojo)
        return this
    }

    override fun writeTree(rootNode: TreeNode?): CirJsonGenerator {
        rootNode ?: return writeNull()

        if (myObjectWriteContext == null) {
            appendValue(CirJsonToken.VALUE_EMBEDDED_OBJECT, rootNode)
            return this
        }

        myObjectWriteContext!!.writeTree(this, rootNode)
        return this
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: binary
     *******************************************************************************************************************
     */

    override fun writeBinary(variant: Base64Variant, data: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        val copy = data.copyOfRange(offset, offset + length)
        return writePOJO(copy)
    }

    /**
     * Although we could support this method, it does not necessarily make sense: we cannot make good use of streaming
     * because buffer must hold all the data. Because of this, currently this will simply throw
     * [UnsupportedOperationException]
     */
    override fun writeBinary(variant: Base64Variant, data: InputStream, dataLength: Int): Int {
        throw UnsupportedOperationException()
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: native ids
     *******************************************************************************************************************
     */

    override val isAbleWriteTypeId: Boolean
        get() = myHasNativeTypeIds

    override fun writeTypeId(id: Any): CirJsonGenerator {
        myTypeId = id
        myHasNativeId = true
        return this
    }

    override fun writeObjectId(referenced: Any): CirJsonGenerator {
        myObjectId = referenced
        myHasNativeId = true
        return this
    }

    override fun writeArrayId(referenced: Any): CirJsonGenerator {
        myObjectId = referenced
        myHasNativeId = true
        return this
    }

    override fun writeEmbeddedObject(obj: Any?): CirJsonGenerator {
        obj ?: return writeNull()
        appendValue(CirJsonToken.VALUE_EMBEDDED_OBJECT, obj)
        return this
    }

    /*
     *******************************************************************************************************************
     * CirJsonGenerator implementation: pass-through copy
     *******************************************************************************************************************
     */

    override fun copyCurrentEvent(parser: CirJsonParser) {
        if (myMayHaveNativeIds) {
            checkNativeIds(parser)
        }

        when (parser.currentToken()) {
            CirJsonToken.START_OBJECT -> {
                writeStartObject()
            }

            CirJsonToken.END_OBJECT -> {
                writeEndObject()
            }

            CirJsonToken.START_ARRAY -> {
                writeStartArray()
            }

            CirJsonToken.END_ARRAY -> {
                writeEndArray()
            }

            CirJsonToken.CIRJSON_ID_PROPERTY_NAME, CirJsonToken.PROPERTY_NAME -> {
                writeName(parser.currentName!!)
            }

            CirJsonToken.VALUE_STRING -> {
                if (parser.isTextCharactersAvailable) {
                    writeString(parser.textCharacters!!, parser.textOffset, parser.textLength)
                } else {
                    writeString(parser.text)
                }
            }

            CirJsonToken.VALUE_NUMBER_INT -> {
                when (parser.numberType!!) {
                    CirJsonParser.NumberType.INT -> writeNumber(parser.intValue)
                    CirJsonParser.NumberType.BIG_INTEGER -> writeLazyInteger(parser.numberValueDeferred)
                    else -> writeNumber(parser.longValue)
                }
            }

            CirJsonToken.VALUE_NUMBER_FLOAT -> {
                writeLazyDecimal(parser.numberValueDeferred)
            }

            CirJsonToken.VALUE_TRUE -> {
                writeBoolean(true)
            }

            CirJsonToken.VALUE_FALSE -> {
                writeBoolean(false)
            }

            CirJsonToken.VALUE_NULL -> {
                writeNull()
            }

            CirJsonToken.VALUE_EMBEDDED_OBJECT -> {
                writePOJO(parser.embeddedObject)
            }

            else -> {
                throw RuntimeException("Internal error: unexpected token: ${parser.currentToken()}")
            }
        }
    }

    override fun copyCurrentStructure(parser: CirJsonParser) {
        var token =
                parser.currentToken() ?: throw UnexpectedEndOfInputException(parser, null, "Unexpected end-of-input")

        if (token == CirJsonToken.CIRJSON_ID_PROPERTY_NAME || token == CirJsonToken.PROPERTY_NAME) {
            if (myMayHaveNativeIds) {
                checkNativeIds(parser)
            }

            writeName(parser.currentName!!)
            token = parser.nextToken() ?: throw UnexpectedEndOfInputException(parser, null, "Unexpected end-of-input")
        }

        when (token) {
            CirJsonToken.START_ARRAY -> {
                if (myMayHaveNativeIds) {
                    checkNativeIds(parser)
                }

                writeStartArray()
                copyBufferContents(parser)
            }

            CirJsonToken.START_OBJECT -> {
                if (myMayHaveNativeIds) {
                    checkNativeIds(parser)
                }

                writeStartObject()
                copyBufferContents(parser)
            }

            CirJsonToken.END_ARRAY -> {
                writeEndArray()
            }

            CirJsonToken.END_OBJECT -> {
                writeEndObject()
            }

            else -> {
                copyBufferValue(parser, token)
            }
        }
    }

    protected open fun copyBufferContents(parser: CirJsonParser) {
        var depths = 1
        lateinit var token: CirJsonToken

        while (parser.nextToken()?.also { token = it } != null) {
            when (token) {
                CirJsonToken.CIRJSON_ID_PROPERTY_NAME, CirJsonToken.PROPERTY_NAME -> {
                    if (myMayHaveNativeIds) {
                        checkNativeIds(parser)
                    }

                    writeName(parser.currentName!!)
                }

                CirJsonToken.START_ARRAY -> {
                    if (myMayHaveNativeIds) {
                        checkNativeIds(parser)
                    }

                    writeStartArray()
                    ++depths
                }

                CirJsonToken.START_OBJECT -> {
                    if (myMayHaveNativeIds) {
                        checkNativeIds(parser)
                    }

                    writeStartObject()
                    ++depths
                }

                CirJsonToken.END_ARRAY -> {
                    writeEndArray()

                    if (--depths == 0) {
                        return
                    }
                }

                CirJsonToken.END_OBJECT -> {
                    writeEndObject()

                    if (--depths == 0) {
                        return
                    }
                }

                else -> {
                    copyBufferValue(parser, token)
                }
            }
        }
    }

    private fun copyBufferValue(parser: CirJsonParser, token: CirJsonToken) {
        if (myMayHaveNativeIds) {
            checkNativeIds(parser)
        }

        when (token) {
            CirJsonToken.VALUE_STRING -> {
                if (parser.isTextCharactersAvailable) {
                    writeString(parser.textCharacters!!, parser.textOffset, parser.textLength)
                } else {
                    writeString(parser.text)
                }
            }

            CirJsonToken.VALUE_NUMBER_INT -> {
                when (parser.numberType!!) {
                    CirJsonParser.NumberType.INT -> writeNumber(parser.intValue)
                    CirJsonParser.NumberType.BIG_INTEGER -> writeLazyInteger(parser.numberValueDeferred)
                    else -> writeNumber(parser.longValue)
                }
            }

            CirJsonToken.VALUE_NUMBER_FLOAT -> {
                writeLazyDecimal(parser.numberValueDeferred)
            }

            CirJsonToken.VALUE_TRUE -> {
                writeBoolean(true)
            }

            CirJsonToken.VALUE_FALSE -> {
                writeBoolean(false)
            }

            CirJsonToken.VALUE_NULL -> {
                writeNull()
            }

            CirJsonToken.VALUE_EMBEDDED_OBJECT -> {
                writePOJO(parser.embeddedObject)
            }

            else -> {
                throw RuntimeException("Internal error: unexpected token: ${parser.currentToken()}")
            }
        }
    }

    private fun checkNativeIds(parser: CirJsonParser) {
        if (parser.typeId?.also { myTypeId = it } != null) {
            myHasNativeId = true
        }

        if (parser.objectId?.also { myObjectId = it } != null) {
            myHasNativeId = true
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    /**
     * Method used for appending token known to represent a "simple" scalar value where token is the only information
     */
    protected fun appendValue(type: CirJsonToken) {
        myTokenWriteContext.writeValue()
        val next = if (myHasNativeId) {
            myLast.append(myAppendAt, type, myObjectId, myTypeId)
        } else {
            myLast.append(myAppendAt, type)
        }

        if (next == null) {
            ++myAppendAt
            return
        }

        myLast = next
        myAppendAt = 1
    }

    /**
     * Method used for appending token known to represent a scalar value where there is additional content (text,
     * number) beyond type token
     */
    protected fun appendValue(type: CirJsonToken, value: Any) {
        myTokenWriteContext.writeValue()
        val next = if (myHasNativeId) {
            myLast.append(myAppendAt, type, value, myObjectId, myTypeId)
        } else {
            myLast.append(myAppendAt, type, value)
        }

        if (next == null) {
            ++myAppendAt
            return
        }

        myLast = next
        myAppendAt = 1
    }

    /**
     * Specialized method used for appending an Object property name, appending either [String] or [SerializableString].
     */
    protected fun appendName(value: Any) {
        val next = if (myHasNativeId) {
            myLast.append(myAppendAt, CirJsonToken.PROPERTY_NAME, value, myObjectId, myTypeId)
        } else {
            myLast.append(myAppendAt, CirJsonToken.PROPERTY_NAME, value)
        }

        if (next == null) {
            ++myAppendAt
            return
        }

        myLast = next
        myAppendAt = 1
    }

    /**
     * Specialized method used for appending a structural start Object/Array marker
     */
    protected fun appendStartMarker(type: CirJsonToken) {
        myTokenWriteContext.writeValue()
        val next = if (myHasNativeId) {
            myLast.append(myAppendAt, type, myObjectId, myTypeId)
        } else {
            myLast.append(myAppendAt, type)
        }

        if (next == null) {
            ++myAppendAt
            return
        }

        myLast = next
        myAppendAt = 1
    }

    /**
     * Specialized method used for appending a structural end Object/Array marker
     */
    protected fun appendEndMarker(type: CirJsonToken) {
        val next = myLast.append(myAppendAt, type)

        if (next == null) {
            ++myAppendAt
        } else {
            myLast = next
            myAppendAt = 1
        }

        val context = myTokenWriteContext.parent ?: return
        myTokenWriteContext = context
    }

    override fun <T> reportUnsupportedOperation(): T {
        throw UnsupportedOperationException("Called operation not supported for TokenBuffer")
    }

    /*
     *******************************************************************************************************************
     * Supporting classes
     *******************************************************************************************************************
     */

    protected class Parser(objectReadContext: ObjectReadContext, var mySource: TokenBuffer, firstSegment: Segment,
            val myHasNativeTypeIds: Boolean, val myHasNativeObjectIds: Boolean, parentContext: TokenStreamContext?,
            override val streamReadConstraints: StreamReadConstraints) : ParserMinimalBase(objectReadContext) {

        /*
         ***************************************************************************************************************
         * Configuration
         ***************************************************************************************************************
         */

        val myHasNativeIds = myHasNativeTypeIds || myHasNativeObjectIds

        /*
         ***************************************************************************************************************
         * Parsing state
         ***************************************************************************************************************
         */

        /**
         * Currently active segment
         */
        var mySegment: Segment? = firstSegment

        /**
         * Pointer to current token within the current segment
         */
        var mySegmentPointer = -1

        /**
         * Information about parser context, context in which the next token is to be parsed (root, array, object).
         */
        var myParsingContext = TokenBufferReadContext.createRootContext(parentContext)

        var myClosed = false

        var myByteBuilder: ByteArrayBuilder? = null

        var myLocation: CirJsonLocation? = null

        /*
         ***************************************************************************************************************
         * Public API, config access, capability introspection
         ***************************************************************************************************************
         */

        override fun version(): Version {
            return PackageVersion.VERSION
        }

        override val streamReadCapabilities: CirJacksonFeatureSet<StreamReadCapability>
            get() = DEFAULT_READ_CAPABILITIES

        override fun streamReadInputSource(): TokenBuffer {
            return mySource
        }

        /*
         ***************************************************************************************************************
         * Extended API beyond CirJsonParser
         ***************************************************************************************************************
         */

        fun peekNextToken(): CirJsonToken? {
            if (myClosed) {
                return null
            }

            var segment = mySegment
            var pointer = mySegmentPointer + 1

            if (pointer >= Segment.TOKENS_PER_SEGMENT) {
                pointer = 0
                segment = segment?.next()
            }

            return segment?.type(pointer)
        }

        /*
         ***************************************************************************************************************
         * Closing, related
         ***************************************************************************************************************
         */

        override fun close() {
            myClosed = true
        }

        override fun closeInput() {}

        override fun releaseBuffers() {}

        /*
         ***************************************************************************************************************
         * Public API, traversal
         ***************************************************************************************************************
         */

        override val idName = ID_NAME

        override fun nextToken(): CirJsonToken? {
            if (myClosed || mySegment == null) {
                myCurrentToken = null
                return null
            }

            if (++mySegmentPointer >= Segment.TOKENS_PER_SEGMENT) {
                mySegmentPointer = 0
                mySegment = mySegment!!.next()

                if (mySegment == null) {
                    myCurrentToken = null
                    return null
                }
            }

            myCurrentToken = mySegment!!.type(mySegmentPointer)

            when (myCurrentToken) {
                CirJsonToken.CIRJSON_ID_PROPERTY_NAME, CirJsonToken.PROPERTY_NAME -> {
                    val obj = currentObject()!!
                    val name = obj as? String ?: obj.toString()
                    myParsingContext.currentName = name
                }

                CirJsonToken.START_OBJECT -> {
                    myParsingContext = myParsingContext.createChildObjectContext()
                }

                CirJsonToken.START_ARRAY -> {
                    myParsingContext = myParsingContext.createChildArrayContext()
                }

                CirJsonToken.END_OBJECT, CirJsonToken.END_ARRAY -> {
                    myParsingContext = myParsingContext.parentOrCopy()
                }

                else -> {
                    myParsingContext.updateForValue()
                }
            }

            return myCurrentToken
        }

        override fun nextName(): String? {
            if (myClosed || mySegment == null) {
                return null
            }

            val pointer = mySegmentPointer + 1

            val nextType = mySegment!!.type(pointer)

            if (pointer >= Segment.TOKENS_PER_SEGMENT ||
                    nextType != CirJsonToken.CIRJSON_ID_PROPERTY_NAME && nextType != CirJsonToken.PROPERTY_NAME) {
                val next = nextToken()
                return if (next == CirJsonToken.CIRJSON_ID_PROPERTY_NAME || next == CirJsonToken.PROPERTY_NAME) {
                    currentName
                } else {
                    null
                }
            }

            mySegmentPointer = pointer
            myCurrentToken = if (nextType == CirJsonToken.PROPERTY_NAME) {
                CirJsonToken.PROPERTY_NAME
            } else {
                CirJsonToken.CIRJSON_ID_PROPERTY_NAME
            }
            val obj = currentObject()!!
            val name = obj as? String ?: obj.toString()
            myParsingContext.currentName = name
            return name
        }

        override fun nextNameMatch(matcher: PropertyNameMatcher): Int {
            val string = nextName()

            if (string != null) {
                return matcher.matchName(string)
            }

            if (hasToken(CirJsonToken.END_OBJECT)) {
                return PropertyNameMatcher.MATCH_END_OBJECT
            }

            return PropertyNameMatcher.MATCH_ODD_TOKEN
        }

        override var isClosed: Boolean
            get() = myClosed
            set(value) {
                super.isClosed = value
            }

        /*
         ***************************************************************************************************************
         * Public API, token accessors
         ***************************************************************************************************************
         */

        override val streamReadContext: TokenStreamContext
            get() = myParsingContext

        override fun assignCurrentValue(value: Any?) {
            myParsingContext.assignCurrentValue(value)
        }

        override fun currentValue(): Any? {
            return myParsingContext.currentValue()
        }

        override fun currentTokenLocation(): CirJsonLocation {
            return currentLocation()
        }

        override fun currentLocation(): CirJsonLocation {
            return myLocation ?: CirJsonLocation.NA
        }

        override val currentName: String?
            get() {
                if (myCurrentToken == CirJsonToken.START_OBJECT || myCurrentToken == CirJsonToken.START_ARRAY) {
                    val parent = myParsingContext.parent!!
                    return parent.currentName
                }

                return myParsingContext.currentName
            }

        /*
         ***************************************************************************************************************
         * Public API, access to token information, text
         ***************************************************************************************************************
         */

        override val text: String?
            get() {
                if (myCurrentToken == CirJsonToken.VALUE_STRING ||
                        myCurrentToken == CirJsonToken.CIRJSON_ID_PROPERTY_NAME ||
                        myCurrentToken == CirJsonToken.PROPERTY_NAME) {
                    val obj = currentObject()
                    return obj as? String ?: obj?.toString()
                }

                return when (myCurrentToken) {
                    CirJsonToken.VALUE_NUMBER_INT, CirJsonToken.VALUE_NUMBER_FLOAT -> currentObject()?.toString()
                    else -> myCurrentToken?.token
                }
            }

        override val textCharacters: CharArray?
            get() = text?.toCharArray()

        override val textLength: Int
            get() = text?.length ?: 0

        override val textOffset: Int
            get() = 0

        override val isTextCharactersAvailable: Boolean
            get() = false

        /*
         ***************************************************************************************************************
         * Public API, access to token information, numeric
         ***************************************************************************************************************
         */

        override val isNaN: Boolean
            get() {
                if (myCurrentToken != CirJsonToken.VALUE_NUMBER_FLOAT) {
                    return false
                }

                return when (val value = currentObject()) {
                    is Double -> value.isNotFinite()
                    is Float -> value.isNotFinite()
                    else -> false
                }
            }

        override val intValue: Int
            get() {
                val number = if (myCurrentToken == CirJsonToken.VALUE_NUMBER_INT) {
                    currentObject() as Number
                } else {
                    numberValue(NUMBER_INT, false)
                }

                if (number is Int) {
                    return number
                }

                if (smallerThanInt(number)) {
                    return number.toInt()
                }

                return convertNumberToInt(number)
            }

        override val longValue: Long
            get() {
                val number = if (myCurrentToken == CirJsonToken.VALUE_NUMBER_INT) {
                    currentObject() as Number
                } else {
                    numberValue(NUMBER_LONG, false)
                }

                if (number is Long) {
                    return number
                }

                if (smallerThanLong(number)) {
                    return number.toLong()
                }

                return convertNumberToLong(number)
            }

        override val bigIntegerValue: BigInteger
            get() = when (val number = numberValue(NUMBER_BIG_INTEGER, true)) {
                is BigInteger -> {
                    number
                }

                is BigDecimal -> {
                    streamReadConstraints.validateBigIntegerScale(number.scale())
                    number.toBigInteger()
                }

                else -> {
                    BigInteger.valueOf(number.toLong())
                }
            }

        override val floatValue: Float
            get() = numberValue(NUMBER_FLOAT, false).toFloat()

        override val doubleValue: Double
            get() = numberValue(NUMBER_FLOAT, false).toDouble()

        override val bigDecimalValue: BigDecimal
            get() = when (val number = numberValue(NUMBER_BIG_DECIMAL, true)) {
                is BigDecimal -> number
                is Int -> BigDecimal.valueOf(number.toLong())
                is Long -> BigDecimal.valueOf(number)
                is BigInteger -> BigDecimal(number)
                else -> BigDecimal.valueOf(number.toDouble())
            }

        override val numberType: NumberType?
            get() {
                myCurrentToken ?: return null

                val value = currentObject()

                if (value is String) {
                    return if (myCurrentToken == CirJsonToken.VALUE_NUMBER_FLOAT) {
                        NumberType.BIG_DECIMAL
                    } else {
                        NumberType.BIG_INTEGER
                    }
                }

                if (value !is Number) {
                    return null
                }

                return when (value) {
                    is Int, is Short -> NumberType.INT
                    is Long -> NumberType.LONG
                    is BigInteger -> NumberType.BIG_INTEGER
                    is Float -> NumberType.FLOAT
                    is Double -> NumberType.DOUBLE
                    is BigDecimal -> NumberType.BIG_DECIMAL
                    else -> null
                }
            }

        override val numberTypeFP: NumberTypeFP
            get() {
                if (myCurrentToken != CirJsonToken.VALUE_NUMBER_FLOAT) {
                    return NumberTypeFP.UNKNOWN
                }

                return when (currentObject()) {
                    is Float -> NumberTypeFP.FLOAT32
                    is Double -> NumberTypeFP.DOUBLE64
                    is BigDecimal -> NumberTypeFP.BIG_DECIMAL
                    else -> NumberTypeFP.UNKNOWN
                }
            }

        override val numberValue: Number
            get() = numberValue(-1, false)

        override val numberValueDeferred: Any
            get() = if (myCurrentToken == null || !myCurrentToken!!.isNumeric) {
                throw constructNotNumericType(myCurrentToken, 0)
            } else {
                currentObject()!!
            }

        private fun numberValue(targetNumberType: Int, preferBigNumbers: Boolean): Number {
            if (myCurrentToken == null || !myCurrentToken!!.isNumeric) {
                throw constructNotNumericType(myCurrentToken, targetNumberType)
            }

            val value = currentObject()

            if (value is Number) {
                return value
            }

            if (value !is String) {
                throw IllegalStateException(
                        "Internal error: entry should be a Number, but is of type ${value.className}")
            }

            val length = value.length

            if (myCurrentToken == CirJsonToken.VALUE_NUMBER_INT) {
                if (preferBigNumbers || length >= 19) {
                    return NumberInput.parseBigInteger(value, isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER))
                }

                if (length >= 10) {
                    return NumberInput.parseLong(value)
                }

                return NumberInput.parseInt(value)
            }

            if (preferBigNumbers) {
                return NumberInput.parseBigDecimal(value, isEnabled(StreamReadFeature.USE_FAST_BIG_NUMBER_PARSER))
            }

            return NumberInput.parseDouble(value, isEnabled(StreamReadFeature.USE_FAST_DOUBLE_PARSER))
        }

        private fun smallerThanInt(number: Number): Boolean {
            return number is Short || number is Byte
        }

        private fun smallerThanLong(number: Number): Boolean {
            return number is Int || number is Short || number is Byte
        }

        @Throws(InputCoercionException::class)
        fun convertNumberToInt(number: Number): Int {
            if (number is Long) {
                val result = number.toInt()

                if (number != result.toLong()) {
                    return reportOverflowInt()
                }

                return result
            }

            if (number is BigInteger) {
                if (number !in BIG_INT_MIN_INT..BIG_INT_MAX_INT) {
                    return reportOverflowInt()
                }
            } else if (number is BigDecimal) {
                if (number !in BIG_DECIMAL_MIN_INT..BIG_DECIMAL_MAX_INT) {
                    return reportOverflowInt()
                }
            } else if (number is Double || number is Float) {
                val double = number.toDouble()

                if (double !in DOUBLE_MIN_INT..DOUBLE_MAX_INT) {
                    return reportOverflowInt()
                }

                return double.toInt()
            } else {
                return throwInternal()
            }

            return number.toInt()
        }

        @Throws(InputCoercionException::class)
        fun convertNumberToLong(number: Number): Long {
            if (number is BigInteger) {
                if (number !in BIG_INT_MIN_LONG..BIG_INT_MAX_LONG) {
                    return reportOverflowLong()
                }
            } else if (number is BigDecimal) {
                if (number !in BIG_DECIMAL_MIN_LONG..BIG_DECIMAL_MAX_LONG) {
                    return reportOverflowLong()
                }
            } else if (number is Double || number is Float) {
                val double = number.toDouble()

                if (double !in DOUBLE_MIN_LONG..DOUBLE_MAX_LONG) {
                    return reportOverflowLong()
                }

                return double.toLong()
            } else {
                return throwInternal()
            }

            return number.toLong()
        }

        /*
         ***************************************************************************************************************
         * Public API, access to token information, other
         ***************************************************************************************************************
         */

        override val embeddedObject: Any?
            get() = if (myCurrentToken != CirJsonToken.VALUE_EMBEDDED_OBJECT) null else currentObject()

        @Throws(CirJacksonException::class)
        override fun getBinaryValue(base64Variant: Base64Variant): ByteArray? {
            if (myCurrentToken == CirJsonToken.VALUE_EMBEDDED_OBJECT) {
                val obj = currentObject()

                if (obj is ByteArray) {
                    return obj
                }
            }

            if (myCurrentToken != CirJsonToken.VALUE_STRING) {
                throw constructReadException(
                        "Current token ($myCurrentToken) not VALUE_STRING (or VALUE_EMBEDDED_OBJECT with ByteArray), cannot access as binary")
            }

            val string = text ?: return null

            var builder = myByteBuilder

            if (builder == null) {
                builder = ByteArrayBuilder(100)
                myByteBuilder = builder
            } else {
                builder.reset()
            }

            decodeBase64(string, builder, base64Variant)
            return builder.toByteArray()
        }

        @Throws(CirJacksonException::class)
        override fun readBinaryValue(base64Variant: Base64Variant, output: OutputStream): Int {
            val data = getBinaryValue(base64Variant) ?: return 0

            try {
                output.write(data, 0, data.size)
            } catch (e: IOException) {
                throw wrapIOFailure(e)
            }

            return data.size
        }

        /*
         ***************************************************************************************************************
         * Public API, Public API, native ids
         ***************************************************************************************************************
         */

        override val isReadingTypeIdPossible: Boolean
            get() = myHasNativeTypeIds

        override val typeId: Any?
            get() = mySegment!!.findTypeId(mySegmentPointer)

        override val objectId: Any?
            get() = mySegment!!.findObjectId(mySegmentPointer)

        /*
         ***************************************************************************************************************
         * Internal methods
         ***************************************************************************************************************
         */

        fun currentObject(): Any? {
            return mySegment!![mySegmentPointer]
        }

        override fun handleEOF() {
            return throwInternal()
        }

    }

    /**
     * Individual segment of TokenBuffer that can store up to 16 tokens (limited by 4 bits per token type marker
     * requirement). The current implementation uses a fixed length array; could alternatively use 16 distinct elements
     * and switch statement (slightly more efficient storage, slightly slower access)
     */
    protected class Segment {

        var myNext: Segment? = null

        /**
         * Bit field used to store types of buffered tokens; 4 bits per token. Value `0` is reserved for "not in use"
         */
        var myTokenTypes = 0L

        val myTokens = arrayOfNulls<Any>(TOKENS_PER_SEGMENT)

        private val myNativeIdsLazyInitializer = lazy { TreeMap<Int, Any>() }

        /**
         * Lazily constructed Map for storing native type and object ids, if any
         */
        val myNativeIds by myNativeIdsLazyInitializer

        fun type(index: Int): CirJsonToken? {
            var long = myTokenTypes

            if (index > 0) {
                long = long shr (index shl 2)
            }

            val i = long.toInt() and 0xF
            return TOKEN_TYPES_BY_INDEX[i]
        }

        fun rawType(index: Int): Int {
            var long = myTokenTypes

            if (index > 0) {
                long = long shr (index shl 2)
            }

            return long.toInt() and 0xF
        }

        operator fun get(index: Int): Any? {
            return myTokens[index]
        }

        fun next(): Segment? {
            return myNext
        }

        /**
         * Accessor for checking whether this segment may have native type or object ids.
         */
        fun hasIds(): Boolean {
            return myNativeIdsLazyInitializer.isInitialized()
        }

        fun append(index: Int, tokenType: CirJsonToken): Segment? {
            if (index < TOKENS_PER_SEGMENT) {
                set(index, tokenType)
                return null
            }

            myNext = Segment()
            myNext!!.set(index, tokenType)
            return myNext
        }

        fun append(index: Int, tokenType: CirJsonToken, objectId: Any?, typeId: Any?): Segment? {
            if (index < TOKENS_PER_SEGMENT) {
                set(index, tokenType, objectId, typeId)
                return null
            }

            myNext = Segment()
            myNext!!.set(index, tokenType, objectId, typeId)
            return myNext
        }

        fun append(index: Int, tokenType: CirJsonToken, value: Any?): Segment? {
            if (index < TOKENS_PER_SEGMENT) {
                set(index, tokenType, value)
                return null
            }

            myNext = Segment()
            myNext!!.set(index, tokenType, value)
            return myNext
        }

        fun append(index: Int, tokenType: CirJsonToken, value: Any?, objectId: Any?, typeId: Any?): Segment? {
            if (index < TOKENS_PER_SEGMENT) {
                set(index, tokenType, value, objectId, typeId)
                return null
            }

            myNext = Segment()
            myNext!!.set(index, tokenType, value, objectId, typeId)
            return myNext
        }

        private fun set(index: Int, tokenType: CirJsonToken) {
            var typeCode = tokenType.ordinal.toLong()

            if (index > 0) {
                typeCode = typeCode shl (index shl 2)
            }

            myTokenTypes = myTokenTypes or typeCode
        }

        private fun set(index: Int, tokenType: CirJsonToken, objectId: Any?, typeId: Any?) {
            var typeCode = tokenType.ordinal.toLong()

            if (index > 0) {
                typeCode = typeCode shl (index shl 2)
            }

            myTokenTypes = myTokenTypes or typeCode
            assignNativeIds(index, objectId, typeId)
        }

        private fun set(index: Int, tokenType: CirJsonToken, value: Any?) {
            myTokens[index] = value
            var typeCode = tokenType.ordinal.toLong()

            if (index > 0) {
                typeCode = typeCode shl (index shl 2)
            }

            myTokenTypes = myTokenTypes or typeCode
        }

        private fun set(index: Int, tokenType: CirJsonToken, value: Any?, objectId: Any?, typeId: Any?) {
            myTokens[index] = value
            var typeCode = tokenType.ordinal.toLong()

            if (index > 0) {
                typeCode = typeCode shl (index shl 2)
            }

            myTokenTypes = myTokenTypes or typeCode
            assignNativeIds(index, objectId, typeId)
        }

        private fun assignNativeIds(index: Int, objectId: Any?, typeId: Any?) {
            myNativeIds
            objectId?.let { myNativeIds[objectIdIndex(index)] = it }
            typeId?.let { myNativeIds[typeIdIndex(index)] = it }
        }

        fun findObjectId(index: Int): Any? {
            if (!myNativeIdsLazyInitializer.isInitialized()) {
                return null
            }

            return myNativeIds[objectIdIndex(index)]
        }

        fun findTypeId(index: Int): Any? {
            if (!myNativeIdsLazyInitializer.isInitialized()) {
                return null
            }

            return myNativeIds[typeIdIndex(index)]
        }

        private fun typeIdIndex(index: Int): Int {
            return index + index
        }

        private fun objectIdIndex(index: Int): Int {
            return index + index + 1
        }

        companion object {

            const val TOKENS_PER_SEGMENT = 16

            /**
             * Static array used for fast conversion between token markers and matching [CirJsonToken] instances
             */
            private val TOKEN_TYPES_BY_INDEX = arrayOfNulls<CirJsonToken>(16).apply {
                val tokens = CirJsonToken.entries.toTypedArray()
                tokens.copyInto(this, 1, 1, min(15, tokens.lastIndex))
            }

        }

    }

    companion object {

        const val ID_NAME = "__cirJsonId__"

        val DEFAULT_STREAM_WRITE_FEATURES = StreamWriteFeature.collectDefaults()

        val BOGUS_WRITE_CAPABILITIES = CirJacksonFeatureSet.fromDefaults(StreamWriteCapability.entries)

        /*
         ***************************************************************************************************************
         * Life-cycle: helper factory methods
         ***************************************************************************************************************
         */

        /**
         * Specialized factory method used when we are generating token stream for further processing without tokens
         * coming from specific input token stream.
         */
        fun forGeneration(): TokenBuffer {
            return TokenBuffer(null, false)
        }

        /**
         * Specialized factory method used when we are specifically buffering the contents of a token stream for further
         * processing.
         */
        fun forBuffering(parser: CirJsonParser, context: ObjectReadContext?): TokenBuffer {
            return TokenBuffer(parser, context)
        }

    }

}