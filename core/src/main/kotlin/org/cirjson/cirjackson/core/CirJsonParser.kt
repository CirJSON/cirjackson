package org.cirjson.cirjackson.core

open class CirJsonParser {

    /**
     * Enumeration of possible "native" (optimal) types that can be used for numbers.
     */
    enum class NumberType {

        INT,

        LONG,

        BIG_INTEGER,

        FLOAT,

        DOUBLE,

        BIG_DECIMAL

    }

}