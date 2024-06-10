package org.cirjson.cirjackson.core.extentions

import kotlin.math.pow

infix fun Int.pow(power: Int): Int = (toDouble().pow(power)).toInt()
