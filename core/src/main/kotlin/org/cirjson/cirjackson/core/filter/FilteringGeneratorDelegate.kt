package org.cirjson.cirjackson.core.filter

import org.cirjson.cirjackson.core.*
import org.cirjson.cirjackson.core.util.CirJsonGeneratorDelegate
import java.io.InputStream
import java.io.Reader
import java.math.BigDecimal
import java.math.BigInteger

/**
 * Specialized [CirJsonGeneratorDelegate] that allows use of [TokenFilter] for outputting a subset of content that
 * caller tries to generate.
 *
 * @param delegate Generator to delegate calls to
 *
 * @param filter Filter to use
 *
 * @param myInclusion Definition of inclusion criteria
 *
 * @param myIsAllowingMultipleMatches Whether to allow multiple matches
 *
 * @property myInclusion Flag that determines whether path leading up to included content should also be automatically
 * included or not. If `false`, no path inclusion is done and only explicitly included entries are output; if `true`
 * then path from main level down to match is also included as necessary.
 *
 * @property myIsAllowingMultipleMatches Flag that determines whether filtering will continue after the first match is
 * indicated or not: if `false`, output is based on just the first full match (returning [TokenFilter.INCLUDE_ALL]) and
 * no more checks are made; if `true` then filtering will be applied as necessary until end of content.
 */
open class FilteringGeneratorDelegate(delegate: CirJsonGenerator, filter: TokenFilter,
        protected var myInclusion: TokenFilter.Inclusion?, protected var myIsAllowingMultipleMatches: Boolean) :
        CirJsonGeneratorDelegate(delegate, false) {

    /**
     * Object consulted to determine whether to write parts of content generator is asked to write or not.
     */
    var filter = filter
        protected set

    /**
     * Although delegate has its own output context it is not sufficient since we actually have to keep track of
     * excluded (filtered out) structures as well as ones delegate actually outputs.
     */
    var filterContext: TokenFilterContext? = TokenFilterContext.createRootContext(filter)
        protected set

    /**
     * State that applies to the item within container, used where applicable. Specifically used to pass inclusion state
     * between property name and property, and also used for array elements.
     */
    protected var myItemFilter: TokenFilter? = filter

    /**
     * Accessor for finding number of matches for which [TokenFilter.INCLUDE_ALL] has been returned, where specific
     * token and subtree starting (if structured type) are passed.
     */
    var matchCount = 0
        protected set

    /*
     *******************************************************************************************************************
     * Public API, accessors
     *******************************************************************************************************************
     */

    override val streamWriteContext: TokenStreamContext
        get() = filterContext!!

    override fun assignCurrentValue(value: Any?) {
        super.assignCurrentValue(value)
        filterContext?.assignCurrentValue(value)
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, structural
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeStartArray(): CirJsonGenerator {
        if (myItemFilter == null) {
            filterContext = filterContext!!.createChildArrayContext(null, null, false)
            return this
        }

        if (myItemFilter === TokenFilter.INCLUDE_ALL) {
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, null, true)
            delegate.writeStartArray()
            return this
        }

        myItemFilter = filterContext!!.checkValue(myItemFilter!!)

        if (myItemFilter == null) {
            filterContext = filterContext!!.createChildArrayContext(null, null, false)
            return this
        }

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            myItemFilter = myItemFilter!!.filterStartArray()
        }

        if (myItemFilter === TokenFilter.INCLUDE_ALL) {
            checkParentPath()
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, null, true)
            delegate.writeStartArray()
        } else if (myItemFilter != null && myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
            checkParentPath(false)
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, null, true)
            delegate.writeStartArray()
        } else {
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, null, false)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(currentValue: Any?): CirJsonGenerator {
        if (myItemFilter == null) {
            filterContext = filterContext!!.createChildArrayContext(null, currentValue, false)
            return this
        }

        if (myItemFilter === TokenFilter.INCLUDE_ALL) {
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, currentValue, true)
            delegate.writeStartArray(currentValue)
            return this
        }

        myItemFilter = filterContext!!.checkValue(myItemFilter!!)

        if (myItemFilter == null) {
            filterContext = filterContext!!.createChildArrayContext(null, currentValue, false)
            return this
        }

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            myItemFilter = myItemFilter!!.filterStartArray()
        }

        if (myItemFilter === TokenFilter.INCLUDE_ALL) {
            checkParentPath()
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, currentValue, true)
            delegate.writeStartArray(currentValue)
        } else if (myItemFilter != null && myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
            checkParentPath(false)
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, currentValue, true)
            delegate.writeStartArray(currentValue)
        } else {
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, currentValue, false)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartArray(currentValue: Any?, size: Int): CirJsonGenerator {
        if (myItemFilter == null) {
            filterContext = filterContext!!.createChildArrayContext(null, currentValue, false)
            return this
        }

        if (myItemFilter === TokenFilter.INCLUDE_ALL) {
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, currentValue, true)
            delegate.writeStartArray(currentValue, size)
            return this
        }

        myItemFilter = filterContext!!.checkValue(myItemFilter!!)

        if (myItemFilter == null) {
            filterContext = filterContext!!.createChildArrayContext(null, currentValue, false)
            return this
        }

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            myItemFilter = myItemFilter!!.filterStartArray()
        }

        if (myItemFilter === TokenFilter.INCLUDE_ALL) {
            checkParentPath()
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, currentValue, true)
            delegate.writeStartArray(currentValue, size)
        } else if (myItemFilter != null && myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
            checkParentPath(false)
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, currentValue, true)
            delegate.writeStartArray(currentValue, size)
        } else {
            filterContext = filterContext!!.createChildArrayContext(myItemFilter, currentValue, false)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeEndArray(): CirJsonGenerator {
        filterContext = filterContext!!.closeArray(delegate)
        filterContext?.filter.let { myItemFilter = it }
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(): CirJsonGenerator {
        if (myItemFilter == null) {
            filterContext = filterContext!!.createChildObjectContext(null, null, false)
            return this
        }

        if (myItemFilter === TokenFilter.INCLUDE_ALL) {
            filterContext = filterContext!!.createChildObjectContext(myItemFilter, null, true)
            delegate.writeStartObject()
            return this
        }

        var filter = filterContext!!.checkValue(myItemFilter!!)

        if (filter == null) {
            filterContext = filterContext!!.createChildObjectContext(null, null, false)
            return this
        }

        if (filter !== TokenFilter.INCLUDE_ALL) {
            filter = filter.filterStartObject()
        }

        if (filter === TokenFilter.INCLUDE_ALL) {
            checkParentPath()
            filterContext = filterContext!!.createChildObjectContext(filter, null, true)
            delegate.writeStartObject()
        } else if (filter != null && myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
            checkParentPath(false)
            filterContext = filterContext!!.createChildObjectContext(filter, null, true)
            delegate.writeStartObject()
        } else {
            filterContext = filterContext!!.createChildObjectContext(filter, null, false)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(currentValue: Any?): CirJsonGenerator {
        if (myItemFilter == null) {
            filterContext = filterContext!!.createChildObjectContext(null, currentValue, false)
            return this
        }

        if (myItemFilter === TokenFilter.INCLUDE_ALL) {
            filterContext = filterContext!!.createChildObjectContext(myItemFilter, currentValue, true)
            delegate.writeStartObject(currentValue)
            return this
        }

        var filter = filterContext!!.checkValue(myItemFilter!!)

        if (filter == null) {
            filterContext = filterContext!!.createChildObjectContext(null, currentValue, false)
            return this
        }

        if (filter !== TokenFilter.INCLUDE_ALL) {
            filter = filter.filterStartObject()
        }

        if (filter === TokenFilter.INCLUDE_ALL) {
            checkParentPath()
            filterContext = filterContext!!.createChildObjectContext(filter, currentValue, true)
            delegate.writeStartObject(currentValue)
        } else if (filter != null && myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
            checkParentPath(false)
            filterContext = filterContext!!.createChildObjectContext(filter, currentValue, true)
            delegate.writeStartObject(currentValue)
        } else {
            filterContext = filterContext!!.createChildObjectContext(filter, currentValue, false)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeStartObject(currentValue: Any?, size: Int): CirJsonGenerator {
        if (myItemFilter == null) {
            filterContext = filterContext!!.createChildObjectContext(null, currentValue, false)
            return this
        }

        if (myItemFilter === TokenFilter.INCLUDE_ALL) {
            filterContext = filterContext!!.createChildObjectContext(myItemFilter, currentValue, true)
            delegate.writeStartObject(currentValue, size)
            return this
        }

        var filter = filterContext!!.checkValue(myItemFilter!!)

        if (filter == null) {
            filterContext = filterContext!!.createChildObjectContext(null, currentValue, false)
            return this
        }

        if (filter !== TokenFilter.INCLUDE_ALL) {
            filter = filter.filterStartObject()
        }

        if (filter === TokenFilter.INCLUDE_ALL) {
            checkParentPath()
            filterContext = filterContext!!.createChildObjectContext(filter, currentValue, true)
            delegate.writeStartObject(currentValue, size)
        } else {
            filterContext = filterContext!!.createChildObjectContext(filter, currentValue, false)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeEndObject(): CirJsonGenerator {
        filterContext = filterContext!!.closeObject(delegate)
        filterContext?.filter.let { myItemFilter = it }
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeName(name: String): CirJsonGenerator {
        var state = filterContext!!.setPropertyName(name)
        filterContext!!.assignCurrentValue(currentValue() ?: delegate.currentValue())

        if (state == null) {
            myItemFilter = null
            return this
        }

        if (state === TokenFilter.INCLUDE_ALL) {
            myItemFilter = state
            delegate.writeName(name)
            return this
        }

        state = state.includeProperty(name)
        myItemFilter = state

        if (state === TokenFilter.INCLUDE_ALL) {
            checkPropertyParentPath()
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeName(name: SerializableString): CirJsonGenerator {
        var state = filterContext!!.setPropertyName(name.value)

        if (state == null) {
            myItemFilter = null
            return this
        }

        if (state === TokenFilter.INCLUDE_ALL) {
            myItemFilter = state
            delegate.writeName(name)
            return this
        }

        state = state.includeProperty(name.value)
        myItemFilter = state

        if (state === TokenFilter.INCLUDE_ALL) {
            checkPropertyParentPath()
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writePropertyId(id: Long): CirJsonGenerator {
        return writeName(id.toString())
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, text/String values
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeString(value: String?): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeString(value)) {
                return this
            }

            checkParentPath()
        }

        delegate.writeString(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeString(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeString(String(buffer, offset, length))) {
                return this
            }

            checkParentPath()
        }

        delegate.writeString(buffer, offset, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeString(value: SerializableString): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeString(value.value)) {
                return this
            }

            checkParentPath()
        }

        delegate.writeString(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeString(reader: Reader?, length: Int): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeString(reader, length)) {
                return this
            }

            checkParentPath()
        }

        delegate.writeString(reader, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRawUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        if (checkRawValueWrite()) {
            delegate.writeRawUTF8String(buffer, offset, length)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeUTF8String(buffer: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        if (checkRawValueWrite()) {
            delegate.writeUTF8String(buffer, offset, length)
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, binary/raw content
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeRaw(text: String): CirJsonGenerator {
        if (checkRawValueWrite()) {
            delegate.writeRaw(text)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(text: String, offset: Int, length: Int): CirJsonGenerator {
        if (checkRawValueWrite()) {
            delegate.writeRaw(text, offset, length)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(raw: SerializableString): CirJsonGenerator {
        if (checkRawValueWrite()) {
            delegate.writeRaw(raw)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(buffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        if (checkRawValueWrite()) {
            delegate.writeRaw(buffer, offset, length)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRaw(char: Char): CirJsonGenerator {
        if (checkRawValueWrite()) {
            delegate.writeRaw(char)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: String): CirJsonGenerator {
        if (checkRawValueWrite()) {
            delegate.writeRawValue(text)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: String, offset: Int, length: Int): CirJsonGenerator {
        if (checkRawValueWrite()) {
            delegate.writeRawValue(text, offset, length)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeRawValue(text: CharArray, offset: Int, length: Int): CirJsonGenerator {
        if (checkRawValueWrite()) {
            delegate.writeRawValue(text, offset, length)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeBinary(variant: Base64Variant, data: ByteArray, offset: Int, length: Int): CirJsonGenerator {
        if (checkBinaryWrite()) {
            delegate.writeBinary(variant, data, offset, length)
        }

        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeBinary(variant: Base64Variant, data: InputStream, dataLength: Int): Int {
        return if (checkBinaryWrite()) {
            delegate.writeBinary(variant, data, dataLength)
        } else {
            -1
        }
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, other value types
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Short): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeNumber(value.toInt())) {
                return this
            }

            checkParentPath()
        }

        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Int): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeNumber(value)) {
                return this
            }

            checkParentPath()
        }

        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Long): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeNumber(value)) {
                return this
            }

            checkParentPath()
        }

        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: BigInteger?): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeNumber(value)) {
                return this
            }

            checkParentPath()
        }

        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Double): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeNumber(value)) {
                return this
            }

            checkParentPath()
        }

        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: Float): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeNumber(value)) {
                return this
            }

            checkParentPath()
        }

        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(value: BigDecimal?): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeNumber(value)) {
                return this
            }

            checkParentPath()
        }

        delegate.writeNumber(value)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(encodedValue: String?): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeRawValue()) {
                return this
            }

            checkParentPath()
        }

        delegate.writeNumber(encodedValue)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNumber(encodedValueBuffer: CharArray, offset: Int, length: Int): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeRawValue()) {
                return this
            }

            checkParentPath()
        }

        delegate.writeNumber(encodedValueBuffer, offset, length)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeBoolean(state: Boolean): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val filterState = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (filterState !== TokenFilter.INCLUDE_ALL && !filterState.includeBoolean(state)) {
                return this
            }

            checkParentPath()
        }

        delegate.writeBoolean(state)
        return this
    }

    @Throws(CirJacksonException::class)
    override fun writeNull(): CirJsonGenerator {
        myItemFilter ?: return this

        if (myItemFilter !== TokenFilter.INCLUDE_ALL) {
            val state = filterContext!!.checkValue(myItemFilter!!) ?: return this

            if (state !== TokenFilter.INCLUDE_ALL && !state.includeNull()) {
                return this
            }

            checkParentPath()
        }

        delegate.writeNull()
        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, convenience property-write methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeOmittedProperty(propertyName: String): CirJsonGenerator {
        if (myItemFilter != null) {
            delegate.writeOmittedProperty(propertyName)
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Public API, write methods, Native Ids
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun writeTypeId(id: Any): CirJsonGenerator {
        if (myItemFilter != null) {
            delegate.writeTypeId(id)
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    protected open fun checkParentPath() {
        checkParentPath(true)
    }

    @Throws(CirJacksonException::class)
    protected open fun checkParentPath(isMatch: Boolean) {
        if (isMatch) {
            ++matchCount
        }

        if (myInclusion == TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH) {
            filterContext!!.writePath(delegate)
        } else if (myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
            filterContext!!.ensurePropertyNameWritten(delegate)
        }

        if (isMatch && !myIsAllowingMultipleMatches) {
            filterContext!!.skipParentChecks()
        }
    }

    /**
     * Specialized variant of [checkParentPath] used when checking parent for a property name to be included with value:
     * rules are slightly different.
     *
     * @throws CirJacksonException If there is an issue with possible resulting read
     */
    @Throws(CirJacksonException::class)
    protected open fun checkPropertyParentPath() {
        ++matchCount
        filterContext!!.assignCurrentValue(currentValue() ?: delegate.currentValue())

        if (myInclusion == TokenFilter.Inclusion.INCLUDE_ALL_AND_PATH) {
            filterContext!!.writePath(delegate)
        } else if (myInclusion == TokenFilter.Inclusion.INCLUDE_NON_NULL) {
            filterContext!!.ensurePropertyNameWritten(delegate)
        }

        if (!myIsAllowingMultipleMatches) {
            filterContext!!.skipParentChecks()
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun checkBinaryWrite(): Boolean {
        myItemFilter ?: return false

        return if (myItemFilter === TokenFilter.INCLUDE_ALL) {
            true
        } else if (myItemFilter!!.includeBinary()) {
            checkParentPath()
            true
        } else {
            false
        }
    }

    @Throws(CirJacksonException::class)
    protected open fun checkRawValueWrite(): Boolean {
        myItemFilter ?: return false

        return if (myItemFilter === TokenFilter.INCLUDE_ALL) {
            true
        } else if (myItemFilter!!.includeRawValue()) {
            checkParentPath()
            true
        } else {
            false
        }
    }

}