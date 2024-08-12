package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.StreamReadConstraints
import org.cirjson.cirjackson.core.exception.StreamConstraintsException

class ReadConstrainedTextBuffer(private val myStreamReadConstraints: StreamReadConstraints,
        bufferRecycler: BufferRecycler?) : TextBuffer(bufferRecycler) {

    @Throws(StreamConstraintsException::class)
    override fun validateStringLength(length: Int) {
        myStreamReadConstraints.validateStringLength(length)
    }

}