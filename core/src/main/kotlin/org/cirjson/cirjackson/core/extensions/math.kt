package org.cirjson.cirjackson.core.extensions

import kotlin.math.pow

infix fun Int.pow(power: Int): Int = (toDouble().pow(power)).toInt()
