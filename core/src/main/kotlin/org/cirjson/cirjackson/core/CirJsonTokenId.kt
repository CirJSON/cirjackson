package org.cirjson.cirjackson.core

object CirJsonTokenId {

    /**
     * ID used to represent [CirJsonToken.NOT_AVAILABLE], used in cases where a token may become available when more
     * input is available: this occurs in non-blocking use cases.
     */
    const val ID_NOT_AVAILABLE: Int = -1

    /**
     * ID used to represent the case where no [CirJsonToken] is available: either because [CirJsonParser] has not been
     * advanced to first token, or because no more tokens will be available (end-of-input or explicit closing of
     * parser).
     */
    const val ID_NO_TOKEN: Int = 0

    /**
     * ID used to represent [CirJsonToken.START_OBJECT]
     */
    const val ID_START_OBJECT: Int = 1

    /**
     * ID used to represent [CirJsonToken.END_OBJECT]
     */
    const val ID_END_OBJECT: Int = 2

    /**
     * ID used to represent [CirJsonToken.START_ARRAY]
     */
    const val ID_START_ARRAY: Int = 3

    /**
     * ID used to represent [CirJsonToken.END_ARRAY]
     */
    const val ID_END_ARRAY: Int = 4

    /**
     * ID used to represent [CirJsonToken.CIRJSON_ID_PROPERTY_NAME]
     */
    const val ID_CIRJSON_ID_PROPERTY_NAME: Int = 5

    /**
     * ID used to represent [CirJsonToken.PROPERTY_NAME]
     */
    const val ID_PROPERTY_NAME: Int = 6

    /**
     * ID used to represent [CirJsonToken.VALUE_STRING]
     */
    const val ID_STRING: Int = 7

    /**
     * ID used to represent [CirJsonToken.VALUE_NUMBER_INT]
     */
    const val ID_NUMBER_INT: Int = 8

    /**
     * ID used to represent [CirJsonToken.VALUE_NUMBER_FLOAT]
     */
    const val ID_NUMBER_FLOAT: Int = 9

    /**
     * ID used to represent [CirJsonToken.VALUE_TRUE]
     */
    const val ID_TRUE: Int = 10

    /**
     * ID used to represent [CirJsonToken.VALUE_FALSE]
     */
    const val ID_FALSE: Int = 11

    /**
     * ID used to represent [CirJsonToken.VALUE_NULL]
     */
    const val ID_NULL: Int = 12

    /**
     * ID used to represent [CirJsonToken.VALUE_EMBEDDED_OBJECT]
     */
    const val ID_EMBEDDED_OBJECT: Int = 13

}