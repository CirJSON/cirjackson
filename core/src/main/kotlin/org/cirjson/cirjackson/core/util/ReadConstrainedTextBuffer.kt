package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.StreamReadConstraints

class ReadConstrainedTextBuffer(private val myStreamReadConstraints: StreamReadConstraints,
        bufferRecycler: BufferRecycler?) : TextBuffer(bufferRecycler) {

    override fun validateStringLength(length: Int) {
        myStreamReadConstraints.validateStringLength(length)
    }

}