package org.cirjson.cirjackson.databind.util

/**
 * Helper class that contains a set of distinct builders for different arrays of primitive values. It also provides a
 * trivially simple reuse scheme, which assumes that caller knows not to use instances concurrently (which works ok with
 * primitive arrays since they cannot contain other non-primitive types). Also note that instances are not thread safe;
 * the intent is that a builder is constructed on a per-call (deserialization) basis.
 */
class ArrayBuilders {

    val booleanBuilder by lazy {
        BooleanBuilder()
    }

    val byteBuilder by lazy {
        ByteBuilder()
    }

    val shortBuilder by lazy {
        ShortBuilder()
    }

    val intBuilder by lazy {
        IntBuilder()
    }

    val longBuilder by lazy {
        LongBuilder()
    }

    val floatBuilder by lazy {
        FloatBuilder()
    }

    val doubleBuilder by lazy {
        DoubleBuilder()
    }

    /*
     *******************************************************************************************************************
     * Implementation classes
     *******************************************************************************************************************
     */

    class BooleanBuilder : PrimitiveArrayBuilder<BooleanArray>() {

        override fun constructArray(length: Int): BooleanArray {
            return BooleanArray(length)
        }

    }

    class ByteBuilder : PrimitiveArrayBuilder<ByteArray>() {

        override fun constructArray(length: Int): ByteArray {
            return ByteArray(length)
        }

    }

    class ShortBuilder : PrimitiveArrayBuilder<ShortArray>() {

        override fun constructArray(length: Int): ShortArray {
            return ShortArray(length)
        }

    }

    class IntBuilder : PrimitiveArrayBuilder<IntArray>() {

        override fun constructArray(length: Int): IntArray {
            return IntArray(length)
        }

    }

    class LongBuilder : PrimitiveArrayBuilder<LongArray>() {

        override fun constructArray(length: Int): LongArray {
            return LongArray(length)
        }

    }

    class FloatBuilder : PrimitiveArrayBuilder<FloatArray>() {

        override fun constructArray(length: Int): FloatArray {
            return FloatArray(length)
        }

    }

    class DoubleBuilder : PrimitiveArrayBuilder<DoubleArray>() {

        override fun constructArray(length: Int): DoubleArray {
            return DoubleArray(length)
        }

    }

    companion object {

        /**
         * Helper method used for constructing simple value comparator used for comparing arrays for content equality.
         *
         * Note: current implementation is not optimized for speed; if performance ever becomes an issue, it is possible
         * to construct much more efficient typed instances (one for `Array<Any>` and subtypes; one per primitive type).
         */
        fun getArrayComparator(defaultValue: Any): Any {
            val length = java.lang.reflect.Array.getLength(defaultValue)
            val defaultValueType = defaultValue::class

            return object : Any() {

                override fun equals(other: Any?): Boolean {
                    other ?: return false
                    if (other === this) {
                        return true
                    }

                    if (!other.hasClass(defaultValueType)) {
                        return false
                    }

                    if (java.lang.reflect.Array.getLength(other) != length) {
                        return false
                    }

                    for (i in 0..<length) {
                        val value1 = java.lang.reflect.Array.get(defaultValue, i)
                        val value2 = java.lang.reflect.Array.get(other, i)

                        if (value1 === value2) {
                            continue
                        }

                        if (value1 != null) {
                            if (value1 != value2) {
                                return false
                            }
                        }
                    }

                    return true
                }

            }
        }

        /**
         * Helper method for constructing a new array that contains specified element followed by contents of the given
         * array but never contains duplicates. If the element already existed, one of two things happens: if the
         * element was already the first one in the array, the array is returned as is; but if not, a new copy is
         * created in which element has moved as the head.
         */
        @Suppress("UNCHECKED_CAST")
        fun <T> insertInListNoDup(array: Array<T>, element: T): Array<T> {
            val length = array.size

            var index = 0

            while (index < length) {
                if (array[index] !== element) {
                    ++index
                    continue
                }

                if (index == 0) {
                    return array
                }

                val result = java.lang.reflect.Array.newInstance(array::class.java.componentType, length) as Array<T>
                array.copyInto(result, 1, 0, index)
                result[0] = element
                ++index

                if (length > index) {
                    array.copyInto(result, index, index)
                }

                return result
            }

            val result = java.lang.reflect.Array.newInstance(array::class.java.componentType, length + 1) as Array<T>

            if (length > 0) {
                array.copyInto(result, 1)
            }

            result[0] = element
            return result
        }

    }

}