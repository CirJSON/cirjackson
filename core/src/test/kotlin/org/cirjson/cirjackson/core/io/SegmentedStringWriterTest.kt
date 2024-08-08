package org.cirjson.cirjackson.core.io

import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.base.GeneratorBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import org.cirjson.cirjackson.core.util.BufferRecycler
import org.cirjson.cirjackson.core.util.CirJsonRecyclerPools
import kotlin.test.*

class SegmentedStringWriterTest {

    @Test
    fun testSimple() {
        val bufferRecycler = BufferRecycler()
        val writer = SegmentedStringWriter(bufferRecycler)
        val builder = StringBuilder()

        var i = 0

        while (builder.length < 100) {
            val number = i.toString()
            builder.append(' ').append(number)
            writer.append(' ')

            when (i.mod(6)) {
                0 -> {
                    writer.append(number)
                }

                1 -> {
                    val string = "  $number"
                    writer.append(string, 2, string.length)
                }

                2 -> {
                    writer.write(number.toCharArray())
                }

                3 -> {
                    val ch = " $number ".toCharArray()
                    writer.write(ch, 1, number.length)
                }

                4 -> {
                    writer.write(number)
                }

                5 -> {
                    val string = "  $number"
                    writer.write(string, 2, number.length)
                }
            }

            ++i
        }

        writer.flush()
        writer.close()

        val expected = builder.toString()
        val actual = writer.contentAndClear
        assertEquals(expected, actual)
    }

    @Test
    fun testBufferRecyclerReuse() {
        val factory = CirJsonFactory()
        val bufferRecycler = BufferRecycler().withPool(CirJsonRecyclerPools.newBoundedPool(3))

        val writer = SegmentedStringWriter(bufferRecycler)
        assertSame(bufferRecycler, writer.bufferRecycler())

        val generator = factory.createGenerator(ObjectWriteContext.empty(), writer)
        val context = (generator as GeneratorBase).ioContext
        assertSame(bufferRecycler, context.bufferRecycler)
        assertTrue(context.bufferRecycler!!.isLinkedWithPool)

        generator.writeStartArray()
        generator.writeArrayId(listOf<String>())
        generator.writeEndArray()
        generator.close()

        assertTrue(bufferRecycler.isLinkedWithPool)

        assertEquals("[\"0\"]", writer.contentAndClear)
        assertTrue(bufferRecycler.isLinkedWithPool)

        bufferRecycler.releaseToPool()
        assertFalse(bufferRecycler.isLinkedWithPool)
    }

}