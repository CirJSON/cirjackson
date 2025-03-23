package org.cirjson.cirjackson.annotations

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class ObjectIdTest {

    @Test
    fun testObjectIdGenerator() {
        val key = ObjectIdGenerator.IDKey(String::class, Any::class, "id1")
        assertNotEquals(0, key.hashCode())
        assertEquals(key, key)
        assertEquals("[ObjectId: key=id1, type=kotlin.String, scope=kotlin.Any]", key.toString())

        val key2 = ObjectIdGenerator.IDKey(Int::class, Any::class, "id2")
        assertNotEquals(key, key2)
        assertNotEquals(key2, key)
    }

    @Test
    fun testIntSequenceGenerator() {
        val generator = ObjectIdGenerators.IntSequenceGenerator()
        var id = generator.generateId("foo")
        assertEquals(-1, id)
        id = generator.generateId("foo")
        assertEquals(0, id)
    }

    @Test
    fun testStringIdGenerator() {
        val generator = ObjectIdGenerators.StringIdGenerator()
        val id = generator.generateId("foo")
        assertNotNull(id)
    }

    @Test
    fun testUUIDGenerator() {
        val generator = ObjectIdGenerators.UUIDGenerator()
        val id = generator.generateId("foo")
        assertNotNull(id)
    }

}