package org.cirjson.cirjackson.core

/**
 * Defines API for accessing configuration and state exposed by higher level databind functionality during read (token
 * stream to Object deserialization) process. Access is mostly needed during construction of [CirJsonParser] instances
 * by [TokenStreamFactory].
 */
interface ObjectReadContext {
}