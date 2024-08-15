package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.base.GeneratorBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import kotlin.test.*

class ByteArrayBuilderTest : TestBase() {

    @Test
    fun testSimple() {
        val builder = ByteArrayBuilder(null, 20)
        assertContentEquals(byteArrayOf(), builder.toByteArray())

        builder.write(0)
        builder.append(1)

        val foo = ByteArray(98) { (2 + it).toByte() }
        builder.write(foo)

        val result = builder.toByteArray()
        assertEquals(100, result.size)

        for (i in result.indices) {
            assertEquals(i, result[i].toInt())
        }

        builder.release()
        builder.close()
    }

    @Test
    fun testAppendFourBytesWithPositive() {
        val bufferRecycler = BufferRecycler()
        val builder = ByteArrayBuilder(bufferRecycler)

        assertEquals(0, builder.size)
        builder.appendFourBytes(2)

        assertEquals(4, builder.size)
        assertEquals(0, builder.toByteArray()[0])
        assertEquals(0, builder.toByteArray()[1])
        assertEquals(0, builder.toByteArray()[2])
        assertEquals(2, builder.toByteArray()[3])
        builder.close()
    }

    @Test
    fun testAppendTwoBytesWithZero() {
        val builder = ByteArrayBuilder(0)

        assertEquals(0, builder.size)

        builder.appendTwoBytes(0)

        assertEquals(2, builder.size)
        assertEquals(0, builder.toByteArray()[0])
        builder.close()
    }

    @Test
    fun testFinishCurrentSegment() {
        val bufferRecycler = BufferRecycler()
        val builder = ByteArrayBuilder(bufferRecycler, 2)
        builder.appendThreeBytes(2)

        assertEquals(3, builder.currentSegmentLength)

        builder.finishCurrentSegment()

        assertEquals(0, builder.currentSegmentLength)
        builder.close()
    }

    @Test
    fun testBufferRecyclerReuse() {
        val factory = CirJsonFactory()
        val bufferRecycler = BufferRecycler().withPool(CirJsonRecyclerPools.newBoundedPool(3))

        val builder = ByteArrayBuilder(bufferRecycler, 20)
        assertSame(bufferRecycler, builder.bufferRecycler())

        val generator = factory.createGenerator(ObjectWriteContext.empty(), builder)
        val ioContext = (generator as GeneratorBase).ioContext
        assertSame(bufferRecycler, ioContext.bufferRecycler)
        assertTrue(ioContext.bufferRecycler!!.isLinkedWithPool)

        generator.writeStartArray()
        generator.writeArrayId(intArrayOf())
        generator.writeEndArray()
        generator.close()

        assertTrue(bufferRecycler.isLinkedWithPool)

        val result = builder.getClearAndRelease()
        assertEquals("[\"0\"]", String(result, Charsets.UTF_8))
        assertTrue(bufferRecycler.isLinkedWithPool)

        bufferRecycler.releaseToPool()
        assertFalse(bufferRecycler.isLinkedWithPool)
    }

}