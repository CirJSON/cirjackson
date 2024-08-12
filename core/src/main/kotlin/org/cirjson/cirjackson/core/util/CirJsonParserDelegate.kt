package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.async.NonBlockingInputFeeder
import org.cirjson.cirjackson.core.exception.InputCoercionException
import org.cirjson.cirjackson.core.symbols.PropertyNameMatcher
import org.cirjson.cirjackson.core.type.ResolvedType
import org.cirjson.cirjackson.core.type.TypeReference
import java.io.OutputStream
import java.io.Writer
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Helper class that implements [delegation pattern](http://en.wikipedia.org/wiki/Delegation_pattern) for
 * [CirJsonParser], to allow for simple overridability of basic parsing functionality. The idea is that any
 * functionality to be modified can be simply overridden; and anything else will be delegated by default.
 */
open class CirJsonParserDelegate(delegate: CirJsonParser) : CirJsonParser() {

    /**
     * Accessor for getting the immediate [CirJsonParser] this parser delegates calls to.
     */
    var delegate = delegate
        protected set

    override fun version(): Version {
        return delegate.version()
    }

    override val streamReadContext: TokenStreamContext?
        get() = delegate.streamReadContext

    override val objectReadContext: ObjectReadContext
        get() = delegate.objectReadContext

    override fun currentTokenLocation(): CirJsonLocation {
        return delegate.currentTokenLocation()
    }

    override fun currentLocation(): CirJsonLocation {
        return delegate.currentLocation()
    }

    override fun streamReadInputSource(): Any? {
        return delegate.streamReadInputSource()
    }

    override fun currentValue(): Any? {
        return delegate.currentValue()
    }

    override fun assignCurrentValue(value: Any?) {
        delegate.assignCurrentValue(value)
    }

    /*
     *******************************************************************************************************************
     * Public API, configuration
     *******************************************************************************************************************
     */

    override fun isEnabled(feature: StreamReadFeature): Boolean {
        return delegate.isEnabled(feature)
    }

    override val streamReadFeatures: Int
        get() = delegate.streamReadFeatures

    override val schema: FormatSchema?
        get() = delegate.schema

    /*
     *******************************************************************************************************************
     * Public API, capability introspection
     *******************************************************************************************************************
     */

    override val isParsingAsyncPossible: Boolean
        get() = delegate.isParsingAsyncPossible

    override fun nonBlockingInputFeeder(): NonBlockingInputFeeder? {
        return delegate.nonBlockingInputFeeder()
    }

    override val streamReadCapabilities: CirJacksonFeatureSet<StreamReadCapability>
        get() = delegate.streamReadCapabilities

    override val streamReadConstraints: StreamReadConstraints
        get() = delegate.streamReadConstraints

    /*
     *******************************************************************************************************************
     * Closeable implementation
     *******************************************************************************************************************
     */

    override fun close() {
        delegate.close()
    }

    override val isClosed: Boolean
        get() = delegate.isClosed

    /*
     *******************************************************************************************************************
     * Public API, token accessors
     *******************************************************************************************************************
     */

    override fun currentToken(): CirJsonToken? {
        return delegate.currentToken()
    }

    override fun currentTokenId(): Int {
        return delegate.currentTokenId()
    }

    override val currentName: String?
        get() = delegate.currentName

    override val isCurrentTokenNotNull: Boolean
        get() = delegate.isCurrentTokenNotNull

    override fun hasTokenId(id: Int): Boolean {
        return delegate.hasTokenId(id)
    }

    override fun hasToken(token: CirJsonToken): Boolean {
        return delegate.hasToken(token)
    }

    override val isExpectedStartArrayToken: Boolean
        get() = delegate.isExpectedStartArrayToken

    override val isExpectedStartObjectToken: Boolean
        get() = delegate.isExpectedStartObjectToken

    override val isExpectedNumberIntToken: Boolean
        get() = delegate.isExpectedNumberIntToken

    override val isNaN: Boolean
        get() = delegate.isNaN

    /*
     *******************************************************************************************************************
     * Public API, token state overrides
     *******************************************************************************************************************
     */

    override fun clearCurrentToken() {
        delegate.clearCurrentToken()
    }

    override val lastClearedToken: CirJsonToken?
        get() = delegate.lastClearedToken

    /*
     *******************************************************************************************************************
     * Public API, iteration over token stream
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun nextToken(): CirJsonToken? {
        return delegate.nextToken()
    }

    @Throws(CirJacksonException::class)
    override fun nextValue(): CirJsonToken? {
        return delegate.nextValue()
    }

    @Throws(CirJacksonException::class)
    override fun finishToken() {
        delegate.finishToken()
    }

    @Throws(CirJacksonException::class)
    override fun skipChildren(): CirJsonParser {
        delegate.skipChildren()
        return this
    }

    @Throws(CirJacksonException::class)
    override fun nextName(): String? {
        return delegate.nextName()
    }

    @Throws(CirJacksonException::class)
    override fun nextName(string: SerializableString): Boolean {
        return delegate.nextName(string)
    }

    @Throws(CirJacksonException::class)
    override fun nextNameMatch(matcher: PropertyNameMatcher): Int {
        return delegate.nextNameMatch(matcher)
    }

    @Throws(CirJacksonException::class)
    override fun currentNameMatch(matcher: PropertyNameMatcher): Int {
        return delegate.currentNameMatch(matcher)
    }

    override val idName: String
        get() = delegate.idName

    /*
     *******************************************************************************************************************
     * Public API, access to token information, text
     *******************************************************************************************************************
     */

    @get:Throws(CirJacksonException::class)
    override val text: String?
        get() = delegate.text

    override val isTextCharactersAvailable: Boolean
        get() = delegate.isTextCharactersAvailable

    @get:Throws(CirJacksonException::class)
    override val textCharacters: CharArray?
        get() = delegate.textCharacters

    @get:Throws(CirJacksonException::class)
    override val textLength: Int
        get() = delegate.textLength

    @get:Throws(CirJacksonException::class)
    override val textOffset: Int
        get() = delegate.textOffset

    @Throws(CirJacksonException::class)
    override fun getText(writer: Writer): Int {
        return delegate.getText(writer)
    }

    /*
     *******************************************************************************************************************
     * Public API, access to token information, numeric
     *******************************************************************************************************************
     */

    @get:Throws(InputCoercionException::class)
    override val byteValue: Byte
        get() = delegate.byteValue

    @get:Throws(InputCoercionException::class)
    override val shortValue: Short
        get() = delegate.shortValue

    @get:Throws(InputCoercionException::class)
    override val intValue: Int
        get() = delegate.intValue

    @get:Throws(InputCoercionException::class)
    override val longValue: Long
        get() = delegate.longValue

    @get:Throws(InputCoercionException::class)
    override val bigIntegerValue: BigInteger
        get() = delegate.bigIntegerValue

    @get:Throws(InputCoercionException::class)
    override val floatValue: Float
        get() = delegate.floatValue

    @get:Throws(InputCoercionException::class)
    override val doubleValue: Double
        get() = delegate.doubleValue

    @get:Throws(InputCoercionException::class)
    override val bigDecimalValue: BigDecimal
        get() = delegate.bigDecimalValue

    override val numberType: NumberType
        get() = delegate.numberType

    override val numberTypeFP: NumberTypeFP?
        get() = delegate.numberTypeFP

    @get:Throws(InputCoercionException::class)
    override val numberValue: Number
        get() = delegate.numberValue

    @get:Throws(InputCoercionException::class)
    override val numberValueExact: Number
        get() = delegate.numberValueExact

    @get:Throws(InputCoercionException::class)
    override val numberValueDeferred: Any
        get() = delegate.numberValueDeferred

    /*
     *******************************************************************************************************************
     * Public API, access to token information, coercion/conversion
     *******************************************************************************************************************
     */

    @get:Throws(InputCoercionException::class)
    override val valueAsInt: Int
        get() = delegate.valueAsInt

    @Throws(InputCoercionException::class)
    override fun getValueAsInt(defaultValue: Int): Int {
        return delegate.getValueAsInt(defaultValue)
    }

    @get:Throws(InputCoercionException::class)
    override val valueAsLong: Long
        get() = delegate.valueAsLong

    @Throws(InputCoercionException::class)
    override fun getValueAsLong(defaultValue: Long): Long {
        return delegate.getValueAsLong(defaultValue)
    }

    @get:Throws(InputCoercionException::class)
    override val valueAsDouble: Double
        get() = delegate.valueAsDouble

    @Throws(InputCoercionException::class)
    override fun getValueAsDouble(defaultValue: Double): Double {
        return delegate.getValueAsDouble(defaultValue)
    }

    override val valueAsBoolean: Boolean
        get() = delegate.valueAsBoolean

    override fun getValueAsBoolean(defaultValue: Boolean): Boolean {
        return delegate.getValueAsBoolean(defaultValue)
    }

    override val valueAsString: String?
        get() = delegate.valueAsString

    override fun getValueAsString(defaultValue: String?): String? {
        return delegate.getValueAsString(defaultValue)
    }

    /*
     *******************************************************************************************************************
     * Public API, access to token information, other
     *******************************************************************************************************************
     */

    @get:Throws(InputCoercionException::class)
    override val booleanValue: Boolean
        get() = delegate.booleanValue

    override val embeddedObject: Any?
        get() = delegate.embeddedObject

    @Throws(CirJacksonException::class)
    override fun getBinaryValue(base64Variant: Base64Variant): ByteArray {
        return delegate.getBinaryValue(base64Variant)
    }

    @Throws(CirJacksonException::class)
    override fun readBinaryValue(base64Variant: Base64Variant, output: OutputStream): Int {
        return delegate.readBinaryValue(base64Variant, output)
    }

    /*
     *******************************************************************************************************************
     * Public API, databind callbacks via `ObjectReadContext`
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun <T> readValueAs(valueType: Class<T>): T {
        return delegate.readValueAs(valueType)
    }

    @Throws(CirJacksonException::class)
    override fun <T> readValueAs(valueTypeReference: TypeReference<T>): T {
        return delegate.readValueAs(valueTypeReference)
    }

    @Throws(CirJacksonException::class)
    override fun <T> readValueAs(type: ResolvedType): T {
        return delegate.readValueAs(type)
    }

    @Throws(CirJacksonException::class)
    override fun <T : TreeNode> readValueAsTree(): T? {
        return delegate.readValueAsTree()
    }

    /*
     *******************************************************************************************************************
     * Public API, Native Ids (type, object)
     *******************************************************************************************************************
     */

    override val isReadingTypeIdPossible: Boolean
        get() = delegate.isReadingTypeIdPossible

    override val objectId: Any?
        get() = delegate.objectId

    override val typeId: Any?
        get() = delegate.typeId

}