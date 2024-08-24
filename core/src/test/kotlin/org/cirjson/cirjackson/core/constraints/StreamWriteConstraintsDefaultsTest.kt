package org.cirjson.cirjackson.core.constraints

import org.cirjson.cirjackson.core.StreamWriteConstraints
import kotlin.test.Test
import kotlin.test.assertEquals

class StreamWriteConstraintsDefaultsTest {

    @Test
    fun testOverride() {
        val depth = 123
        val constraints = StreamWriteConstraints.builder().maxNestingDepth(depth).build()

        try {
            StreamWriteConstraints.overrideDefaultStreamWriteConstraints(constraints)
            assertEquals(depth, StreamWriteConstraints.defaults().maxNestingDepth)
        } finally {
            StreamWriteConstraints.overrideDefaultStreamWriteConstraints(null)
            assertEquals(StreamWriteConstraints.DEFAULT_MAX_DEPTH, StreamWriteConstraints.defaults().maxNestingDepth)
        }
    }

}