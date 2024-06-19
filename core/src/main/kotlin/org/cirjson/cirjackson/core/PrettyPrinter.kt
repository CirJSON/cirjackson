package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.exception.CirJacksonIOException
import org.cirjson.cirjackson.core.exception.StreamWriteException
import org.cirjson.cirjackson.core.io.SerializedString
import org.cirjson.cirjackson.core.util.Separators

/**
 * Interface for objects that implement pretty printer functionality, such as indentation. Pretty printers are used to
 * add white space in output CirJSON content, to make results more human-readable. Usually this means things like adding
 * line-feeds and indentation.
 *
 * Note: stateful implementations MUST implement `org.cirjson.cirjackson.core.util.Instantiatable` interface, to allow
 * for constructing  per-generation instances and avoid state corruption. Stateless implementations need not do this;
 * but those are less common.
 */
interface PrettyPrinter {

    /**
     * Method called after a root-level value has been completely output, and before another value is to be output.
     *
     * Default handling (without pretty-printing) will output a space, to allow values to be parsed correctly.
     * Pretty-printer is to output some other suitable and nice-looking separator (tab(s), space(s), linefeed(s) or any
     * combination thereof).
     *
     * @param generator Generator used for output
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeRootValueSeparator(generator: CirJsonGenerator)

    /**
     * Method called when an Object value is to be output, before any fields are output.
     *
     * Default handling (without pretty-printing) will output the opening curly bracket. Pretty-printer is to output a
     * curly bracket as well, but can surround that with other (white-space) decoration.
     *
     * @param generator Generator used for output
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeStartObject(generator: CirJsonGenerator)

    /**
     * Method called after an Object value has been completely output (minus closing curly bracket).
     *
     * Default handling (without pretty-printing) will output the closing curly bracket. Pretty-printer is to output a
     * curly bracket as well, but can surround that with other (white-space) decoration.
     *
     * @param generator Generator used for output
     *
     * @param numberOfEntries Number of direct members of the Object that have been output
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeEndObject(generator: CirJsonGenerator, numberOfEntries: Int)

    /**
     * Method called after an Object entry (field:value) has been completely output, and before another value is to be
     * output.
     *
     * Default handling (without pretty-printing) will output a single comma to separate the two. Pretty-printer is to
     * output a comma as well, but can surround that with other (white-space) decoration.
     *
     * @param generator Generator used for output
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeObjectEntrySeparator(generator: CirJsonGenerator)

    /**
     * Method called after an Object property name has been output, but before the value is output.
     *
     * Default handling (without pretty-printing) will output a single colon to separate the two. Pretty-printer is to
     * output a colon as well, but can surround that with other (white-space) decoration.
     *
     * @param generator Generator used for output
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeObjectNameValueSeparator(generator: CirJsonGenerator)

    /**
     * Method called when an Array value is to be output, before any member/child values are output.
     *
     * Default handling (without pretty-printing) will output the opening bracket. Pretty-printer is to output a bracket
     * as well, but can surround that with other (white-space) decoration.
     *
     * @param generator Generator used for output
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeStartArray(generator: CirJsonGenerator)

    /**
     * Method called after an Array value has been completely output (minus closing bracket).
     *
     * Default handling (without pretty-printing) will output the closing bracket. Pretty-printer is to output a bracket
     * as well, but can surround that with other (white-space) decoration.
     *
     * @param generator Generator used for output
     *
     * @param numberOfEntries Number of direct members of the array that have been output
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeEndArray(generator: CirJsonGenerator, numberOfEntries: Int)

    /**
     * Method called after an array value has been completely output, and before another value is to be output.
     *
     * Default handling (without pretty-printing) will output a single comma to separate the two. Pretty-printer is to
     * output a comma as well, but can surround that with other (white-space) decoration.
     *
     * @param generator Generator used for output
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun writeArrayValueSeparator(generator: CirJsonGenerator)

    /**
     * Method called after array start marker has been output, and right before the first value is to be output. It
     * **is** called for arrays with no values.
     *
     * Default handling does not output anything, but pretty-printer is free to add any white space decoration.
     *
     * @param generator Generator used for output
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun beforeArrayValues(generator: CirJsonGenerator)

    /**
     * Method called after object start marker has been output, and right before the Name of the first property is to be
     * output. It **is** called for objects without properties.
     *
     * Default handling does not output anything, but pretty-printer is free to add any white space decoration.
     *
     * @param generator Generator used for output
     *
     * @throws CirJacksonIOException if there is an underlying I/O problem
     *
     * @throws StreamWriteException for problems in encoding token stream
     */
    @Throws(CirJacksonException::class)
    fun beforeObjectEntries(generator: CirJsonGenerator)

    companion object {

        val DEFAULT_SEPARATORS = Separators.createDefaultInstance()

        /**
         * Default String used for separating root values is single space.
         */
        val DEFAULT_ROOT_VALUE_SEPARATOR = SerializedString(" ")

    }

}