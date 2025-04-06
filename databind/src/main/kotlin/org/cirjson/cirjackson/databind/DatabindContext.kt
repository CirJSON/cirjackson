package org.cirjson.cirjackson.databind

// TODO
abstract class DatabindContext {

    @Throws(DatabindException::class)
    abstract fun <T> reportBadDefinition(type: KotlinType, message: String): T

}