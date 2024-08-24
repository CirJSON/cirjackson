package org.cirjson.cirjackson.core.constraints

import org.cirjson.cirjackson.core.StreamReadConstraints
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamReadConstraintsDefaultsTest {

    @Test
    fun testOverride() {
        val docLength = 10_000_000L
        val numLength = 1234
        val strLength = 12345
        val depth = 123
        val nameLength = 2000

        val constraints = StreamReadConstraints.builder().maxDocumentLength(docLength).maxNumberLength(numLength)
                .maxStringLength(strLength).maxNestingDepth(depth).maxNameLength(nameLength).build()

        try {
            StreamReadConstraints.overrideDefaultStreamReadConstraints(constraints)
            assertEquals(docLength, StreamReadConstraints.defaults().maxDocumentLength)
            assertEquals(numLength, StreamReadConstraints.defaults().maxNumberLength)
            assertEquals(strLength, StreamReadConstraints.defaults().maxStringLength)
            assertEquals(depth, StreamReadConstraints.defaults().maxNestingDepth)
            assertEquals(nameLength, StreamReadConstraints.defaults().maxNameLength)
        } finally {
            StreamReadConstraints.overrideDefaultStreamReadConstraints(null)
            assertEquals(StreamReadConstraints.DEFAULT_MAX_DOC_LEN, StreamReadConstraints.defaults().maxDocumentLength)
            assertEquals(StreamReadConstraints.DEFAULT_MAX_NUM_LEN, StreamReadConstraints.defaults().maxNumberLength)
            assertEquals(StreamReadConstraints.DEFAULT_MAX_STRING_LEN, StreamReadConstraints.defaults().maxStringLength)
            assertEquals(StreamReadConstraints.DEFAULT_MAX_DEPTH, StreamReadConstraints.defaults().maxNestingDepth)
            assertEquals(StreamReadConstraints.DEFAULT_MAX_NAME_LEN, StreamReadConstraints.defaults().maxNameLength)
        }
    }

}