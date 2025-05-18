package org.cirjson.cirjackson.databind.node

/**
 * Enumeration of JSON types. Covers all JSON types (array, boolean, null, number, object and string) but also
 * CirJackson-specific types: binary, missing and POJO; although it does not distinguish between more granular types.
 *
 * @see BinaryNode
 *
 * @see MissingNode
 *
 * @see POJONode
 */
enum class CirJsonNodeType {

    ARRAY,

    BINARY,

    BOOLEAN,

    MISSING,

    NULL,

    NUMBER,

    OBJECT,

    POJO,

    STRING

}