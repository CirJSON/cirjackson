package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.ObjectWriteContext
import org.cirjson.cirjackson.core.TestBase
import org.cirjson.cirjackson.core.base.GeneratorBase
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory
import java.io.OutputStream
import kotlin.test.*

class BufferRecyclerPoolTest : TestBase() {

    @Test
    fun testNoOp() {
        checkBufferRecyclerPool(CirJsonRecyclerPools.nonRecyclingPool(), checkPooledResource = false,
                implementsClear = true)
    }

    @Test
    fun testThreadLocal() {
        checkBufferRecyclerPool(CirJsonRecyclerPools.threadLocalPool(), checkPooledResource = true,
                implementsClear = false)
    }

    @Test
    fun testConcurrentDequeue() {
        checkBufferRecyclerPool(CirJsonRecyclerPools.newConcurrentDequePool(), checkPooledResource = true,
                implementsClear = true)
    }

    @Test
    fun testBounded() {
        checkBufferRecyclerPool(CirJsonRecyclerPools.newBoundedPool(1), checkPooledResource = true,
                implementsClear = true)
    }

    @Test
    fun testPluggingPool() {
        checkBufferRecyclerPool(TestPool(), checkPooledResource = true, implementsClear = true)
    }

    private fun checkBufferRecyclerPool(pool: RecyclerPool<BufferRecycler>, checkPooledResource: Boolean,
            implementsClear: Boolean) {
        val cirJsonFactory = CirJsonFactory.builder().recyclerPool(pool).build()
        val usedBufferRecycler = write("test", cirJsonFactory, 6)

        if (checkPooledResource) {
            val pooledBufferRecycler = pool.acquireAndLinkPooled()
            assertSame(usedBufferRecycler, pooledBufferRecycler)
            pooledBufferRecycler.releaseToPool()
        }

        if (implementsClear) {
            assertTrue(pool.clear())

            val pooledBufferRecycler = pool.acquireAndLinkPooled()
            assertNotNull(pooledBufferRecycler)
            assertNotSame(usedBufferRecycler, pooledBufferRecycler)
        } else {
            assertFalse(pool.clear())
        }
    }

    @Suppress("SameParameterValue")
    private fun write(value: String, cirJsonFactory: CirJsonFactory, expectedSize: Int): BufferRecycler {
        lateinit var bufferRecycler: BufferRecycler
        val output = NoOpOutputStream()

        cirJsonFactory.createGenerator(ObjectWriteContext.empty(), output).use { generator ->
            bufferRecycler = (generator as GeneratorBase).ioContext.bufferRecycler!!
            generator.writeString(value)
        }

        assertEquals(expectedSize, output.size)
        return bufferRecycler
    }

    private class NoOpOutputStream : OutputStream() {

        var size = 0

        override fun write(b: Int) {
            ++size
        }

        override fun write(b: ByteArray) {
            size += b.size
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            size += len
        }

    }

    private class TestPool : RecyclerPool<BufferRecycler> {

        var bufferRecycler: BufferRecycler? = null

        override fun acquirePooled(): BufferRecycler {
            if (bufferRecycler == null) {
                return BufferRecycler()
            }

            return bufferRecycler!!.also { bufferRecycler = null }
        }

        override fun releasePooled(pooled: BufferRecycler) {
            if (bufferRecycler === pooled) {
                throw IllegalStateException("BufferRecycler released more than once")
            }

            bufferRecycler = pooled
        }

        override fun clear(): Boolean {
            bufferRecycler = null
            return true
        }

        override fun pooledCount(): Int {
            return bufferRecycler?.let { 1 } ?: 0
        }
    }

}