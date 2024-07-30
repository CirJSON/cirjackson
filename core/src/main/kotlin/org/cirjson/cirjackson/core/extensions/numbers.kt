package org.cirjson.cirjackson.core.extensions

/**
 * Helper method to verify whether given `Double` value is finite (regular rational number) or not (NaN or Infinity).
 *
 * @return `true` if number is NOT finite (is Infinity or NaN); `false` otherwise
 */
fun Double.isNotFinite(): Boolean {
    return !isFinite()
}

/**
 * Helper method to verify whether given `Float` value is finite (regular rational number) or not (NaN or Infinity).
 *
 * @return `true` if number is NOT finite (is Infinity or NaN); `false` otherwise
 */
fun Float.isNotFinite(): Boolean {
    return !isFinite()
}
