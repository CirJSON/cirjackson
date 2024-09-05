package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.io.NumberInput
import java.io.IOException
import java.io.Writer
import kotlin.math.max
import kotlin.math.min

/**
 * TextBuffer is a class similar to [StringBuffer], with the following differences:
 * * TextBuffer uses segments character arrays, to avoid having to do additional array copies when array is not big
 * enough. This means that only reallocating that is necessary is done only once: if and when caller wants to access
 * contents in a linear array (CharArray, String).
 * * TextBuffer can also be initialized in "shared mode", in which it will just act as a wrapper to a single char array
 * managed by another object (like parser that owns it)
 * * TextBuffer is not synchronized.
 */
open class TextBuffer(val bufferRecycler: BufferRecycler?) {

    /**
     * Shared input buffer; stored here in case some input can be returned as is, without being copied to collector's
     * own buffers. Note that this is read-only for this Object.
     */
    private var myInputBuffer: CharArray? = null

    private var myInputStart = 0

    /**
     * Character offset of first char in input buffer; -1 to indicate that input buffer currently does not contain any
     * useful char data
     */
    private var myInputLength = 0

    private val mySegmentsDelegate = lazy {
        ArrayList<CharArray>(4)
    }

    /**
     * List of segments prior to currently active segment.
     */
    private val mySegments by mySegmentsDelegate

    /**
     * Flag that indicates whether [mySegments] is non-empty
     */
    private var myHasSegments = false

    /**
     * Amount of characters in segments in [mySegments]
     */
    private var mySegmentSize = 0

    private var myCurrentSegment: CharArray? = null

    /**
     * Number of characters in currently active (last) segment
     */
    var currentSegmentSize = 0

    private var myResultString: String? = null

    private var myResultCharArray: CharArray? = null

    protected constructor(allocator: BufferRecycler?, initialSegment: CharArray) : this(allocator) {
        myCurrentSegment = initialSegment
        currentSegmentSize = initialSegment.size
        myInputStart = -1
    }

    /*
     *******************************************************************************************************************
     * Life-cycle
     *******************************************************************************************************************
     */

    /**
     * Method called to indicate that the underlying buffers should now be recycled if they haven't yet been recycled.
     * Although caller can still use this text buffer, it is not advisable to call this method if that is likely, since
     * next time a buffer is needed, buffers need to be reallocated.
     *
     * Calling this method will NOT clear already aggregated contents (that is, `myCurrentSegment`, to retain current
     * token text if (but only if!) already aggregated.
     */
    fun releaseBuffers() {
        myInputStart = -1
        currentSegmentSize = 0
        myInputLength = 0

        myInputBuffer = null
        myResultCharArray = null

        if (myHasSegments) {
            clearSegments()
        }

        if (bufferRecycler != null) {
            if (myCurrentSegment != null) {
                val buffer = myCurrentSegment!!
                myCurrentSegment = null
                bufferRecycler.releaseCharBuffer(BufferRecycler.CHAR_TEXT_BUFFER, buffer)
            }
        }
    }

    private fun clearSegments() {
        myHasSegments = false
        mySegments.clear()
        currentSegmentSize = 0
        mySegmentSize = 0
    }

    /**
     * Method called to clear out any content text buffer may have, and initializes buffer to use non-shared data.
     */
    fun resetWithEmpty() {
        myInputStart = -1
        currentSegmentSize = 0
        myInputLength = 0

        myInputBuffer = null
        myResultCharArray = null
        myResultString = null

        if (myHasSegments) {
            clearSegments()
        }
    }

    /**
     * Method for clearing out possibly existing content, and replacing them with a single-character content (so [size]
     * would return `1`)
     *
     * @param char Character to set as the buffer contents
     */
    fun resetWith(char: Char) {
        myInputStart = -1
        myInputLength = 0

        myResultCharArray = null
        myResultString = null

        if (myHasSegments) {
            clearSegments()
        } else if (myCurrentSegment == null) {
            myCurrentSegment = buffer(1)
        }

        myCurrentSegment!![0] = char
        currentSegmentSize = 1
        mySegmentSize = 1
    }

    private fun buffer(needed: Int): CharArray {
        return bufferRecycler?.allocateCharBuffer(BufferRecycler.CHAR_TEXT_BUFFER, needed) ?: CharArray(
                max(needed, MIN_SEGMENT_LEN))
    }

    /**
     * Method called to initialize the buffer with a shared copy of data; this means that buffer will just have pointers
     * to actual data. It also means that if anything is to be appended to the buffer, it will first have to unshare it
     * (make a local copy).
     *
     * @param buffer Buffer that contains shared contents
     *
     * @param offset Offset of the first content character in `buffer`
     *
     * @param length Length of content in `buffer`
     */
    fun resetWithShared(buffer: CharArray, offset: Int, length: Int) {
        myResultCharArray = null
        myResultString = null

        myInputBuffer = buffer
        myInputStart = offset
        myInputLength = length

        if (myHasSegments) {
            clearSegments()
        }
    }

    /**
     * Method called to initialize the buffer with a shared copy of data; the data is then appended to the buffer.
     *
     * @param buffer Buffer that contains new contents
     *
     * @param offset Offset of the first content character in `buffer`
     *
     * @param length Length of content in `buffer`
     *
     * @throws CirJacksonException if the buffer has grown too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @Throws(CirJacksonException::class)
    fun resetWithCopy(buffer: CharArray, offset: Int, length: Int) {
        myInputBuffer = null
        myInputStart = -1
        myInputLength = 0

        myResultCharArray = null
        myResultString = null

        if (myHasSegments) {
            clearSegments()
        } else if (myCurrentSegment == null) {
            myCurrentSegment = buffer(length)
        }

        currentSegmentSize = 0
        mySegmentSize = 0
        append(buffer, offset, length)
    }

    /**
     * Method called to initialize the buffer with a String; the data is then appended to the buffer.
     *
     * @param text String that contains new contents
     *
     * @param start Offset of the first content character in `text`
     *
     * @param length Length of content in `text`
     *
     * @throws CirJacksonException if the buffer has grown too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @Throws(CirJacksonException::class)
    fun resetWithCopy(text: String, start: Int, length: Int) {
        myInputBuffer = null
        myInputStart = -1
        myInputLength = 0

        myResultCharArray = null
        myResultString = null

        if (myHasSegments) {
            clearSegments()
        } else if (myCurrentSegment == null) {
            myCurrentSegment = buffer(length)
        }

        currentSegmentSize = 0
        mySegmentSize = 0
        append(text, start, length)
    }

    /**
     * Method for clearing out possibly existing content, and replacing them with a single-character content (so [size]
     * would return `1`)
     *
     * @param value to replace existing buffer
     *
     * @throws CirJacksonException if the value is too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @Throws(CirJacksonException::class)
    fun resetWithString(value: String) {
        myInputBuffer = null
        myInputStart = -1
        myInputLength = 0

        validateStringLength(value.length)
        myResultString = value
        myResultCharArray = null

        if (myHasSegments) {
            clearSegments()
        }

        currentSegmentSize = 0
    }

    /**
     * Accessor for the currently active (last) content segment without changing state of the buffer
     *
     * @return Currently active (last) content segment
     */
    val bufferWithoutReset: CharArray?
        get() = myCurrentSegment

    /*
     *******************************************************************************************************************
     * Accessors for implementing public interface
     *******************************************************************************************************************
     */

    val size: Int
        get() {
            return if (myInputStart >= 0) {
                myInputStart
            } else {
                myResultCharArray?.size ?: myResultString?.length ?: (mySegmentSize + currentSegmentSize)
            }
        }

    val textOffset: Int
        get() = max(myInputStart, 0)

    val isHavingTextAsCharacters: Boolean
        get() = myInputStart >= 0 || myResultCharArray != null || myResultString == null

    /**
     * Accessor that may be used to get the contents of this buffer as a single `CharArray` regardless of whether
     * they were collected in a segmented fashion or not: this typically require allocation of the result buffer.
     *
     * @return Aggregated `CharArray` that contains all buffered content
     *
     * @throws CirJacksonException if the text is too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @get:Throws(CirJacksonException::class)
    val textBuffer: CharArray
        get() {
            return when {
                myInputStart >= 0 -> myInputBuffer!!
                myResultCharArray != null -> myResultCharArray!!
                myResultString != null -> myResultString!!.toCharArray().also { myResultCharArray = it }
                !myHasSegments -> myCurrentSegment ?: charArrayOf()
                else -> contentsAsArray()
            }
        }

    /*
     *******************************************************************************************************************
     * Other accessors
     *******************************************************************************************************************
     */

    /**
     * Accessor that may be used to get the contents of this buffer as a single `String` regardless of whether they were
     * collected in a segmented fashion or not: this typically require construction of the result String.
     *
     * @return Aggregated buffered contents as a [String]
     *
     * @throws CirJacksonException if the contents are too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @Throws(CirJacksonException::class)
    fun contentsAsString(): String {
        if (myResultString != null) {
            return myResultString!!
        }

        if (myResultCharArray != null) {
            return String(myResultCharArray!!).also { myResultString = it }
        }

        if (myInputStart >= 0) {
            if (myInputLength < 1) {
                return "".also { myResultString = it }
            }

            validateStringLength(myInputLength)
            return String(myInputBuffer!!, myInputStart, myInputLength).also { myResultString = it }
        }

        val segmentLength = mySegmentSize
        val currentLength = currentSegmentSize

        if (segmentLength == 0) {
            if (currentLength == 0) {
                return "".also { myResultString = it }
            }

            validateStringLength(myInputLength)
            return String(myCurrentSegment!!, 0, currentLength).also { myResultString = it }
        }

        val builderLength = segmentLength + currentLength

        if (builderLength < 0) {
            reportBufferOverflow(segmentLength, currentLength)
        }

        validateStringLength(builderLength)
        val stringBuilder = StringBuilder(builderLength)

        if (mySegmentsDelegate.isInitialized()) {
            for (segment in mySegments) {
                stringBuilder.appendRange(segment, 0, segment.size)
            }
        }

        stringBuilder.appendRange(myCurrentSegment!!, 0, currentSegmentSize)
        return stringBuilder.toString().also { myResultString = it }
    }

    /**
     * Accessor that may be used to get the contents of this buffer as a single `CharArray` regardless of whether they
     * were collected in a segmented fashion or not: this typically require construction of the result CharArray.
     *
     * @return Aggregated buffered contents as a [CharArray]
     *
     * @throws CirJacksonException if the contents are too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @Throws(CirJacksonException::class)
    fun contentsAsArray(): CharArray {
        return myResultCharArray ?: resultArray().also { myResultCharArray = it }
    }

    @Throws(CirJacksonException::class)
    private fun resultArray(): CharArray {
        if (myResultString != null) {
            return myResultString!!.toCharArray()
        }

        if (myInputStart >= 0) {
            val length = myInputLength

            if (length < 1) {
                return NO_CHARS
            }

            validateStringLength(length)
            val start = myInputStart

            return if (start == 0) {
                myInputBuffer!!.copyOf(length)
            } else {
                myInputBuffer!!.copyOfRange(start, start + length)
            }
        }

        val size = size

        if (size < 1) {
            if (size < 0) {
                reportBufferOverflow(mySegmentSize, currentSegmentSize)
            }

            return NO_CHARS
        }

        validateStringLength(size)
        var offset = 0
        val result = CharArray(size)

        if (mySegmentsDelegate.isInitialized()) {
            for (current in mySegments) {
                val currentLength = current.size
                current.copyInto(result, offset)
                offset += currentLength
            }
        }

        myCurrentSegment!!.copyInto(result, offset, 0, currentSegmentSize)
        return result
    }

    /**
     * Convenience method for converting contents of the buffer into a Double value.
     *
     * NOTE! Caller **MUST** validate contents before calling this method, to ensure textual version is valid CirJSON
     * floating-point token -- this method is not guaranteed to do any validation and behavior with invalid content is
     * not defined (either throws an exception or returns arbitrary number).
     *
     * @param useFastParser whether to use `FastDoubleParser`
     *
     * @return Buffered text value parsed as a [Double], if possible
     *
     * @throws NumberFormatException may (but is not guaranteed!) be thrown if contents are not a valid CirJSON
     * floating-point number representation
     */
    @Throws(NumberFormatException::class)
    fun contentsAsDouble(useFastParser: Boolean): Double {
        return when {
            myResultString != null -> NumberInput.parseDouble(myResultString!!, useFastParser)

            myInputStart >= 0 -> NumberInput.parseDouble(myInputBuffer!!, myInputStart, myInputLength, useFastParser)

            currentSegmentSize >= 0 -> NumberInput.parseDouble(myCurrentSegment!!, 0, currentSegmentSize,
                    useFastParser)

            myResultCharArray != null -> NumberInput.parseDouble(myResultCharArray!!, useFastParser)

            else -> NumberInput.parseDouble(contentsAsString(), useFastParser)
        }
    }

    /**
     * Convenience method for converting contents of the buffer into a Float value.
     *
     * NOTE! Caller **MUST** validate contents before calling this method, to ensure textual version is valid CirJSON
     * floating-point token -- this method is not guaranteed to do any validation and behavior with invalid content is
     * not defined (either throws an exception or returns arbitrary number).
     *
     * @param useFastParser whether to use `FastDoubleParser`
     *
     * @return Buffered text value parsed as a [Float], if possible
     *
     * @throws NumberFormatException may (but is not guaranteed!) be thrown if contents are not a valid CirJSON
     * floating-point number representation
     */
    fun contentAsFloat(useFastParser: Boolean): Float {
        return when {
            myResultString != null -> NumberInput.parseFloat(myResultString!!, useFastParser)

            myInputStart >= 0 -> NumberInput.parseFloat(myInputBuffer!!, myInputStart, myInputLength, useFastParser)

            currentSegmentSize >= 0 -> NumberInput.parseFloat(myCurrentSegment!!, 0, currentSegmentSize,
                    useFastParser)

            myResultCharArray != null -> NumberInput.parseFloat(myResultCharArray!!, useFastParser)

            else -> NumberInput.parseFloat(contentsAsString(), useFastParser)
        }
    }

    /**
     * Specialized convenience method that will decode a 32-bit int, of at most 9 digits (and possible leading minus
     * sign).
     *
     * NOTE: method DOES NOT verify that the contents actually are a valid Java `Int` value (either regarding format, or
     * value range): caller MUST validate that.
     *
     * @param neg Whether contents start with a minus sign
     *
     * @return Buffered text value parsed as an `Int` using [NumberInput.parseInt] method (which does NOT validate
     * input)
     */
    fun contentAsInt(neg: Boolean): Int {
        return if (myInputStart >= 0 && myInputBuffer != null) {
            if (neg) {
                -NumberInput.parseInt(myInputBuffer!!, myInputStart + 1, myInputLength - 1)
            } else {
                NumberInput.parseInt(myInputBuffer!!, myInputStart, myInputLength)
            }
        } else if (neg) {
            -NumberInput.parseInt(myCurrentSegment!!, 1, currentSegmentSize - 1)
        } else {
            NumberInput.parseInt(myCurrentSegment!!, 0, currentSegmentSize)
        }
    }

    /**
     * Specialized convenience method that will decode a 64-bit int, of at most 18 digits (and possible leading minus
     * sign).
     *
     * NOTE: method DOES NOT verify that the contents actually are a valid Java `Long` value (either regarding format,
     * or value range): caller MUST validate that.
     *
     * @param neg Whether contents start with a minus sign
     *
     * @return Buffered text value parsed as an `Long` using [NumberInput.parseLong] method (which does NOT validate
     * input)
     *
     * @since 2.9
     */
    fun contentAsLong(neg: Boolean): Long {
        return if (myInputStart >= 0 && myInputBuffer != null) {
            if (neg) {
                -NumberInput.parseLong(myInputBuffer!!, myInputStart + 1, myInputLength - 1)
            } else {
                NumberInput.parseLong(myInputBuffer!!, myInputStart, myInputLength)
            }
        } else if (neg) {
            -NumberInput.parseLong(myCurrentSegment!!, 1, currentSegmentSize - 1)
        } else {
            NumberInput.parseLong(myCurrentSegment!!, 0, currentSegmentSize)
        }
    }

    /**
     * Accessor that will write buffered contents using given [Writer].
     *
     * @param writer Writer to use for writing out buffered content
     *
     * @return Number of characters written (same as [size])
     *
     * @throws IOException if the write using given `Writer` fails (exception that `Writer` throws)
     */
    @Throws(CirJacksonException::class, IOException::class)
    fun contentsToWriter(writer: Writer): Int {
        if (myResultCharArray != null) {
            writer.write(myResultCharArray!!)
            return myResultCharArray!!.size
        }

        if (myResultString != null) {
            writer.write(myResultString!!)
            return myResultString!!.length
        }

        if (myInputStart >= 0) {
            val length = myInputLength

            if (length > 0) {
                writer.write(myInputBuffer!!, myInputStart, length)
            }

            return length
        }

        var total = 0

        if (mySegmentsDelegate.isInitialized()) {
            for (segment in mySegments) {
                val currentLength = segment.size
                total += currentLength
                writer.write(segment, 0, currentLength)
            }
        }

        val length = currentSegmentSize

        if (length > 0) {
            total += length
            writer.write(myCurrentSegment!!, 0, length)
        }

        return total
    }

    /*
     *******************************************************************************************************************
     * Public mutators
     *******************************************************************************************************************
     */

    /**
     * Method called to make sure that buffer is not using shared input
     * buffer; if it is, it will copy such contents to private buffer.
     */
    fun ensureNotShared() {
        if (myInputStart >= 0) {
            unshare(16)
        }
    }

    /**
     * Appends a character to the buffer.
     *
     * @param char char to append
     *
     * @throws CirJacksonException if the buffer has grown too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @Throws(CirJacksonException::class)
    fun append(char: Char) {
        if (myInputStart >= 0) {
            unshare(16)
        }

        myResultCharArray = null
        myResultString = null

        var current = myCurrentSegment!!

        if (currentSegmentSize >= current.size) {
            validateAppend(1)
            expand()
            current = myCurrentSegment!!
        }

        current[currentSegmentSize++] = char
    }

    /**
     * Method called if/when we need to append content when we have been initialized to use shared buffer.
     */
    private fun unshare(needExtra: Int) {
        val sharedLength = myInputLength
        myInputLength = 0
        val inputBuffer = myInputBuffer
        myInputBuffer = null
        val start = myInputStart
        myInputStart = -1

        val needed = sharedLength + needExtra

        if (myCurrentSegment == null || needed > myCurrentSegment!!.size) {
            myCurrentSegment = buffer(needed)
        }

        if (sharedLength > 0) {
            inputBuffer!!.copyInto(myCurrentSegment!!, 0, start, start + sharedLength)
        }

        mySegmentSize = 0
        currentSegmentSize = sharedLength
    }

    @Throws(CirJacksonException::class)
    private fun validateAppend(toAppend: Int) {
        var newTotalLength = mySegmentSize + currentSegmentSize + toAppend

        if (newTotalLength < 0) {
            newTotalLength = Int.MAX_VALUE
        }

        validateStringLength(newTotalLength)
    }

    private fun expand() {
        val current = myCurrentSegment!!
        myHasSegments = true
        mySegments.add(current)
        mySegmentSize += current.size

        if (mySegmentSize < 0) {
            reportBufferOverflow(mySegmentSize - current.size, current.size)
        }

        currentSegmentSize = 0
        val oldLength = current.size
        val newLength = min(max(oldLength + (oldLength shr 1), MIN_SEGMENT_LEN), MAX_SEGMENT_LEN)
        myCurrentSegment = CharArray(newLength)
    }

    /**
     * Appends the data to the buffer.
     *
     * @param chars char array to append
     *
     * @param start the start index within the array (from which we read chars to append)
     *
     * @param length number of chars to take from the array
     *
     * @throws CirJacksonException if the buffer has grown too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @Throws(CirJacksonException::class)
    fun append(chars: CharArray, start: Int, length: Int) {
        var realStart = start
        var realLength = length

        if (myInputStart >= 0) {
            unshare(realLength)
        }

        myResultCharArray = null
        myResultString = null

        val current = myCurrentSegment!!
        val max = current.size - currentSegmentSize

        if (max >= realLength) {
            chars.copyInto(current, currentSegmentSize, realStart, realStart + realLength)
            currentSegmentSize += realLength
            return
        }

        validateAppend(realLength)

        if (max > 0) {
            chars.copyInto(current, currentSegmentSize, realStart, realStart + max)
            realStart += max
            realLength -= max
        }

        do {
            expand()
            val amount = min(myCurrentSegment!!.size, realLength)
            chars.copyInto(myCurrentSegment!!, 0, realStart, realStart + amount)
            currentSegmentSize += amount
            realStart += amount
            realLength -= amount
        } while (realLength > 0)
    }

    /**
     * Appends the data to the buffer.
     *
     * @param string string to append
     *
     * @param offset the start index within the string (from which we read chars to append)
     *
     * @param length number of chars to take from the string
     *
     * @throws CirJacksonException if the buffer has grown too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @Throws(CirJacksonException::class)
    fun append(string: String, offset: Int, length: Int) {
        var realOffset = offset
        var realLength = length

        if (myInputStart >= 0) {
            unshare(realLength)
        }

        myResultCharArray = null
        myResultString = null

        val current = myCurrentSegment!!
        val max = current.size - currentSegmentSize

        if (max >= realLength) {
            string.toCharArray(current, currentSegmentSize, realOffset, realOffset + realLength)
            currentSegmentSize += realLength
            return
        }

        validateAppend(realLength)

        if (max > 0) {
            string.toCharArray(current, currentSegmentSize, realOffset, realOffset + max)
            realOffset += max
            realLength -= max
        }

        do {
            expand()
            val amount = min(myCurrentSegment!!.size, realLength)
            string.toCharArray(myCurrentSegment!!, 0, realOffset, realOffset + amount)
            currentSegmentSize += amount
            realOffset += amount
            realLength -= amount
        } while (realLength > 0)
    }

    /*
     *******************************************************************************************************************
     * Raw access, for high-performance use
     *******************************************************************************************************************
     */

    val currentSegment: CharArray
        get() {
            if (myInputStart >= 0) {
                unshare(1)
            } else {
                val current = myCurrentSegment

                if (current == null) {
                    myCurrentSegment = buffer(0)
                } else if (currentSegmentSize >= current.size) {
                    expand()
                }
            }

            return myCurrentSegment!!
        }

    fun emptyAndGetCurrentSegment(): CharArray {
        myInputStart = -1
        myInputLength = 0
        currentSegmentSize = 0

        myInputBuffer = null
        myResultCharArray = null
        myResultString = null

        if (myHasSegments) {
            clearSegments()
        }

        return myCurrentSegment ?: buffer(0).also { myCurrentSegment = it }
    }

    /**
     * Convenience method that finishes the current active content segment (by specifying how many characters within
     * consists of valid content) and aggregates and returns resulting contents (similar to a call to
     * [contentsAsString]).
     *
     * @param length Length of content (in characters) of the current active segment
     *
     * @return String that contains all buffered content
     *
     * @throws CirJacksonException if the buffer has grown too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @Throws(CirJacksonException::class)
    fun setCurrentAndReturn(length: Int): String {
        currentSegmentSize = length

        if (mySegmentSize > 0) {
            return contentsAsString()
        }

        val currentLength = currentSegmentSize
        validateStringLength(currentLength)
        val string = if (currentLength != 0) String(myCurrentSegment!!, 0, currentLength) else ""
        myResultString = string
        return string
    }

    /**
     * Finishes the current segment and returns it
     *
     * @return char array
     *
     * @throws CirJacksonException if the text is too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @Throws(CirJacksonException::class)
    fun finishCurrentSegment(): CharArray {
        myHasSegments = true
        mySegments.add(myCurrentSegment!!)
        val oldLength = myCurrentSegment!!.size
        mySegmentSize += oldLength

        if (mySegmentSize < 0) {
            reportBufferOverflow(mySegmentSize - oldLength, oldLength)
        }

        currentSegmentSize = 0
        validateStringLength(mySegmentSize)

        val newLength = min(max(oldLength + (oldLength shr 1), MIN_SEGMENT_LEN), MAX_SEGMENT_LEN)
        val current = CharArray(newLength)
        myCurrentSegment = current
        return current
    }

    /**
     * Finishes the current segment and returns the content as a String
     *
     * @param lastSegmentEnd End offset in the currently active segment, could be 0 in the case of first character is
     * delimiter or end-of-line
     *
     * @param trimTrailingSpaces Whether trailing spaces should be trimmed or not
     *
     * @return token as text
     *
     * @throws CirJacksonException if the text is too large, see
     * [org.cirjson.cirjackson.core.StreamReadConstraints.Builder.maxStringLength]
     */
    @Throws(CirJacksonException::class)
    fun finishAndReturn(lastSegmentEnd: Int, trimTrailingSpaces: Boolean): String {
        if (trimTrailingSpaces) {
            val ptr = lastSegmentEnd - 1

            if (ptr < 0 || myCurrentSegment!![ptr].code <= 0x0020) {
                return doTrim(ptr)
            }
        }

        currentSegmentSize = lastSegmentEnd
        return contentsAsString()
    }

    @Throws(CirJacksonException::class)
    private fun doTrim(ptr: Int): String {
        var pointer = ptr

        while (true) {
            val current = myCurrentSegment!!

            while (--pointer >= 0) {
                if (current[pointer].code > 0x0020) {
                    currentSegmentSize = pointer + 1
                    return contentsAsString()
                }
            }

            if (!mySegmentsDelegate.isInitialized() || mySegments.isEmpty()) {
                break
            }

            myCurrentSegment = mySegments.removeAt(mySegments.lastIndex)
            pointer = myCurrentSegment!!.size
        }

        currentSegmentSize = 0
        myHasSegments = false
        return contentsAsString()
    }

    /**
     * Method called to expand size of the current segment, to accommodate for more contiguous content. Usually only
     * used when parsing tokens like names if even then. Method will both expand the segment and return it.
     *
     * @return Expanded current segment
     */
    fun expandCurrentSegment(): CharArray {
        val current = myCurrentSegment!!
        val length = current.size
        var newLength = length + (length shr 1)

        if (newLength > MAX_SEGMENT_LEN) {
            newLength = length + (length shr 2)
        }

        return current.copyOf(newLength).also { myCurrentSegment = it }
    }

    /**
     * Method called to expand size of the current segment, to accommodate for more contiguous content. Usually only
     * used when parsing tokens like names if even then.
     *
     * @param minSize Required minimum strength of the current segment
     *
     * @return Expanded current segment
     */
    fun expandCurrentSegment(minSize: Int): CharArray {
        val current = myCurrentSegment!!

        if (current.size >= minSize) {
            return current
        }

        return current.copyOf(minSize).also { myCurrentSegment = it }
    }

    /*
     *******************************************************************************************************************
     * Convenience methods for validation
     *******************************************************************************************************************
     */

    protected fun reportBufferOverflow(previous: Int, current: Int) {
        val newSize = previous.toLong() + current.toLong()
        throw IllegalStateException("TextBuffer overrun: size reached ($newSize) exceeds maximum of ${Int.MAX_VALUE}")
    }

    /**
     * Convenience method that can be used to verify that a String of specified length does not exceed maximum specific
     * by this constraints object: if it does, a [CirJacksonException] is thrown.
     *
     * Default implementation does nothing.
     *
     * @param length Length of string in input units
     *
     * @throws CirJacksonException If length exceeds maximum
     */
    @Throws(CirJacksonException::class)
    protected open fun validateStringLength(length: Int) {
        // no-op
    }

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */

    /**
     * Note: calling this method may not be as efficient as calling [contentsAsString], since it's not guaranteed that
     * resulting String is cached.
     */
    override fun toString(): String {
        return try {
            contentsAsString()
        } catch (_: CirJacksonException) {
            "TextBuffer: Exception when reading contents"
        }
    }

    companion object {

        private val NO_CHARS = charArrayOf()

        /**
         * To start with a sizable but not huge buffer, will grow as necessary
         */
        const val MIN_SEGMENT_LEN = 500

        /**
         * To limit maximum segment length to something sensible, 64kc chunks (128 kB).
         */
        const val MAX_SEGMENT_LEN = 0x10000

        /**
         * Factory method for constructing an instance with no allocator, and with initial full segment.
         *
         * @param initialSegment Initial, full segment to use for creating buffer (buffer [size] would return length
         * of `initialSegment`)
         *
         * @return TextBuffer constructed
         */
        fun fromInitial(initialSegment: CharArray): TextBuffer {
            return TextBuffer(null, initialSegment)
        }

    }

}