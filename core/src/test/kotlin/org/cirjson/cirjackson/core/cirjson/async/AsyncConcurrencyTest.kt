package org.cirjson.cirjackson.core.cirjson.async

import org.cirjson.cirjackson.core.AsyncTestBase
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.support.AsyncReaderWrapperBase
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class AsyncConcurrencyTest : AsyncTestBase() {

    private val factory = newStreamFactory()

    @Test
    @Ignore
    fun testConcurrentAsync() {
        for (mode in ALL_ASYNC_MODES) {
            for (round in 1..MAX_ROUNDS) {
                concurrentAsync(mode, 0, round, 99)
                concurrentAsync(mode, 0, round, 3)
                concurrentAsync(mode, 0, round, 1)

                concurrentAsync(mode, 1, round, 99)
                concurrentAsync(mode, 1, round, 3)
                concurrentAsync(mode, 1, round, 1)
            }
        }
    }

    private fun concurrentAsync(mode: Int, padding: Int, round: Int, bytesPerFeed: Int) {
        val executor = Executors.newFixedThreadPool(16)
        val errorCount = AtomicInteger(0)
        val completedCount = AtomicInteger(0)
        val errorReference = AtomicReference<String>()
        val queue = ArrayBlockingQueue<Worker>(20)

        for (i in 0..<7) {
            queue.add(Worker(mode, padding, bytesPerFeed))
        }

        val futures = ArrayList<Future<*>>()

        for (i in 0..<REPETITION_COUNT) {
            val callable = Callable<Void> {
                val worker = queue.take()

                try {
                    if (worker.process()) {
                        completedCount.incrementAndGet()
                    }
                } catch (e: Throwable) {
                    if (errorCount.getAndIncrement() == 0) {
                        errorReference.set(e.toString())
                    }
                } finally {
                    queue.add(worker)
                }

                return@Callable null
            }

            futures.add(executor.submit(callable))
        }

        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)
        val count = errorCount.get()

        if (count > 0) {
            fail("Expected no problems (round $round/$MAX_ROUNDS); got $count, first with: ${errorReference.get()}")
        }

        val completed = completedCount.get()

        if (completed !in EXPECTED_COMPLETED - 10..EXPECTED_COMPLETED) {
            fail("Expected about $EXPECTED_COMPLETED completed rounds, got: $completed")
        }

        while (!queue.isEmpty()) {
            queue.take().close()
        }
    }

    private inner class Worker(private val myMode: Int, private val myPadding: Int, private val myBytesPerFeed: Int) :
            AutoCloseable {

        private var myStage = -1

        private var myParser: AsyncReaderWrapperBase? = null

        private var myErrored = false

        fun process(): Boolean {
            if (myErrored) {
                return false
            }

            try {
                when (myStage++) {
                    -1 -> {
                        myParser = createAsync(factory, myMode, myBytesPerFeed, utf8Bytes(DOC), myPadding)
                    }

                    0 -> {
                        assert(CirJsonToken.START_ARRAY)
                    }

                    1 -> {
                        assert(TEXT1)
                    }

                    2 -> {
                        assert(TEXT2)
                    }

                    3 -> {
                        assert(TEXT3)
                    }

                    4 -> {
                        assert(TEXT4)
                    }

                    5 -> {
                        assert(CirJsonToken.END_ARRAY)
                    }

                    else -> {
                        close()
                        return true
                    }
                }
            } catch (e: Exception) {
                myErrored = true
                throw e
            }

            return false
        }

        override fun close() {
            if (myParser != null) {
                myParser!!.close()
                myParser = null
                myStage = -1
            }
        }

        private fun assert(expected: String) {
            assert(CirJsonToken.VALUE_STRING)
            assertEquals(expected, myParser!!.currentText())
        }

        private fun assert(expectedToken: CirJsonToken) {
            assertToken(expectedToken, myParser!!.nextToken())
        }

    }

    companion object {

        private const val MAX_ROUNDS = 30

        private const val TEXT1 = "Short"

        private const val TEXT2 = "Some longer text"

        private const val TEXT3 = "and yet more"

        private const val TEXT4 = "... Longest yet although not superbly long still (see 'apos'?)"

        private const val DOC = "[\"$TEXT1\", \"$TEXT2\",\n\"$TEXT3\",\"$TEXT4\" ]"

        private const val REPETITION_COUNT = 99000

        private const val EXPECTED_COMPLETED = (REPETITION_COUNT + 7) / 8

    }

}