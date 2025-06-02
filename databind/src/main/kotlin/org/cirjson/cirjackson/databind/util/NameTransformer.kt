package org.cirjson.cirjackson.databind.util

/**
 * Helper class used to encapsulate details of name mangling, transforming of names using different strategies
 * (prefixes, suffixes). The default implementation is "no-operation" (aka identity transformation).
 */
abstract class NameTransformer protected constructor() {

    /**
     * Method called when (forward) transformation is needed.
     */
    abstract fun transform(name: String): String

    /**
     * Method called when reversal of transformation is needed; should return `null` if this is not possible, that is,
     * given name cannot have been the result of calling [transform] of this object.
     */
    abstract fun reverse(transformed: String): String?

    /**
     * Singleton "no-operation" transformer which simply returns given name as is. Used commonly as placeholder or
     * marker.
     */
    object NoOpTransformer : NameTransformer() {

        override fun transform(name: String): String {
            return name
        }

        override fun reverse(transformed: String): String {
            return transformed
        }

    }

    open class Chained(protected val myTransformer1: NameTransformer, protected val myTransformer2: NameTransformer) :
            NameTransformer() {

        override fun transform(name: String): String {
            return myTransformer1.transform(myTransformer2.transform(name))
        }

        override fun reverse(transformed: String): String? {
            return myTransformer1.reverse(transformed)?.let { myTransformer2.reverse(it) }
        }

        override fun toString(): String {
            return "[ChainedTransformer($myTransformer1, $myTransformer2)]"
        }

    }

    companion object {

        /**
         * Factory method for constructing a simple transformer based on prefix and/or suffix.
         */
        fun simpleTransformer(prefix: String?, suffix: String?): NameTransformer {
            return when {
                !prefix.isNullOrEmpty() && !suffix.isNullOrEmpty() -> object : NameTransformer() {

                    override fun transform(name: String): String {
                        return "$prefix$name$suffix"
                    }

                    override fun reverse(transformed: String): String? {
                        if (!transformed.startsWith(prefix)) {
                            return null
                        }

                        val string = transformed.substring(prefix.length)

                        if (!string.endsWith(suffix)) {
                            return null
                        }

                        return string.substring(0, string.length - suffix.length)
                    }

                    override fun toString(): String {
                        return "[PreAndSuffixTransformer('$prefix','$suffix')]"
                    }

                }

                !prefix.isNullOrEmpty() -> object : NameTransformer() {

                    override fun transform(name: String): String {
                        return "$prefix$name"
                    }

                    override fun reverse(transformed: String): String? {
                        if (!transformed.startsWith(prefix)) {
                            return null
                        }

                        return transformed.substring(prefix.length)
                    }

                    override fun toString(): String {
                        return "[PrefixTransformer('$prefix')]"
                    }

                }

                !suffix.isNullOrEmpty() -> object : NameTransformer() {

                    override fun transform(name: String): String {
                        return "$name$suffix"
                    }

                    override fun reverse(transformed: String): String? {
                        if (!transformed.endsWith(suffix)) {
                            return null
                        }

                        return transformed.substring(0, transformed.length - suffix.length)
                    }

                    override fun toString(): String {
                        return "[SuffixTransformer('$prefix')]"
                    }

                }

                else -> NoOpTransformer
            }
        }

        /**
         * Method that constructs a transformer that applies given transformers as a sequence; essentially combines
         * separate transform operations into one logical transformation.
         */
        fun chainedTransformer(transformer1: NameTransformer, transformer2: NameTransformer): NameTransformer {
            return Chained(transformer1, transformer2)
        }

    }

}