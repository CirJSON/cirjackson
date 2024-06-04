package org.cirjson.cirjackson.core

/**
 * The main factory class of CirJackson package, used to configure and construct reader (aka parser, [CirJsonParser])
 * and writer (aka generator, [CirJsonGenerator]) instances.
 *
 * Factory instances are thread-safe and reusable after configuration (if any). Typically, applications and services use
 * only a single globally shared factory instance, unless they need differently configured factories. Factory reuse is
 * important if efficiency matters; most recycling of expensive construct is done on per-factory basis.
 *
 * Creation of a factory instance is a light-weight operation, and since there is no need for pluggable alternative
 * implementations (as there is no "standard" CirJSON processor API to implement), the default constructor is used for
 * constructing factory instances.
 */
class CirJsonFactory {

    /**
     * Enumeration that defines all on/off features that can only be changed for [CirJsonFactory].
     */
    enum class Feature(override val isEnabledByDefault: Boolean) : CirJacksonFeature {

        /**
         * Feature that determines whether CirJSON object field names are to be canonicalized using [String.intern] or
         * not.
         *
         * If enabled, all field names will be `intern`ed (and caller can count on this being true for all such names).
         *
         * If disabled, no `intern`ing is done. There may still be basic canonicalization (that is, same String will be
         * used to represent all identical object property names for a single document).
         *
         * Note: this setting only has effect if [CANONICALIZE_FIELD_NAMES] is true -- otherwise no\* canonicalization
         * of any sort is done.
         *
         * This setting is enabled by default.
         */
        INTERN_FIELD_NAMES(true),

        /**
         * Feature that determines whether CirJSON object field names are to be canonicalized (details of how
         * canonicalization is done then further specified by [INTERN_FIELD_NAMES]).
         *
         * This setting is enabled by default.
         */
        CANONICALIZE_FIELD_NAMES(true),

        /**
         * Feature that determines what happens if we encounter a case in symbol handling where number of hash
         * collisions exceeds a safety threshold -- which almost certainly means a denial-of-service attack via
         * generated duplicate hash codes.
         *
         * If feature is enabled, an [IllegalStateException] is thrown to indicate the suspected denial-of-service
         * attack; if disabled, processing continues but canonicalization (and thereby `intern`ing) is disabled as
         * protective measure.
         *
         * This setting is enabled by default.
         */
        FAIL_ON_SYMBOL_HASH_OVERFLOW(true),

        /**
         * Feature that determines whether we will use a [RecyclerPool] for allocating and possibly recycling
         * [BufferRecycler] or not. The default [RecyclerPool] implementation uses [ThreadLocal] and [SoftReference] for
         * efficient reuse of underlying input/output buffers.
         *
         * This usually makes sense on normal J2SE/J2EE server-side processing; but may not make sense on platforms
         * where [SoftReference] handling is broken (like Android), or if there are retention issues due to
         * [ThreadLocal].
         *
         * Note that the naming here is somewhat misleading as this is used to enable or disable pooling; but the actual
         * pooling implementation is configurable and may not be based on [ThreadLocal].
         *
         * This setting is enabled by default.
         */
        USE_THREAD_LOCAL_FOR_BUFFER_RECYCLING(true),

        /**
         * Feature to control charset detection for byte-based inputs (`byte[]`, [InputStream]...). When this feature is
         * enabled (the default), the factory will allow UTF-16 and UTF-32 inputs and try to detect them, as specified
         * by RFC 4627. When this feature is disabled the factory will assume UTF-8, as specified by RFC 8259.
         *
         * This setting is enabled by default.
         */
        CHARSET_DETECTION(true);

        override val mask: Int = 1 shl ordinal

        override fun isEnabledIn(flags: Int): Boolean {
            return (flags and mask) != 0
        }

        companion object {

            /**
             * Method that calculates bit set (flags) of all features that are enabled by default.
             *
             * @return Bit field of features enabled by default
             */
            fun collectDefaults(): Int {
                var flags = 0

                for (feature in entries) {
                    if (feature.isEnabledByDefault) {
                        flags = flags or feature.mask
                    }
                }

                return flags
            }

        }

    }

}