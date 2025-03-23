package org.cirjson.cirjackson.annotations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OptionalBooleanTest {

    @Test
    fun testProperties() {
        assertTrue(OptionalBoolean.TRUE.asPrimitive())
        assertFalse(OptionalBoolean.FALSE.asPrimitive())
        assertFalse(OptionalBoolean.DEFAULT.asPrimitive())

        assertEquals(OptionalBoolean.TRUE, OptionalBoolean.fromBoolean(true))
        assertEquals(OptionalBoolean.FALSE, OptionalBoolean.fromBoolean(false))
        assertEquals(OptionalBoolean.DEFAULT, OptionalBoolean.fromBoolean(null))
    }

}