package org.cirjson.cirjackson.core.util

/**
 * This is a small utility class, whose main functionality is to allow simple reuse of raw byte/char buffers. It is
 * usually allocated through [RecyclerPool]: multiple pool implementations exists.
 *
 * The default pool implementation uses `ThreadLocal` combined with `SoftReference`. The end result is a low-overhead
 * GC-cleanable recycling: hopefully ideal for use by stream readers.
 */
class BufferRecycler : RecyclerPool.WithPool<BufferRecycler> {
}