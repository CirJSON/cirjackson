package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

class ArrayType private constructor(override val contentType: KotlinType, bindings: TypeBindings?,
        private val myEmptyArray: Any, valueHandler: Any?, typeHandler: Any?, isUsedAsStaticType: Boolean) :
        TypeBase(myEmptyArray::class, bindings, null, null, contentType.hashCode(), valueHandler, typeHandler,
                isUsedAsStaticType) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withContentType(contentType: KotlinType): ArrayType {
        TODO("Not yet implemented")
    }

    override fun withTypeHandler(handler: Any?): ArrayType {
        TODO("Not yet implemented")
    }

    override fun withContentTypeHandler(handler: Any?): ArrayType {
        TODO("Not yet implemented")
    }

    override fun withValueHandler(handler: Any?): ArrayType {
        TODO("Not yet implemented")
    }

    override fun withContentValueHandler(handler: Any?): ArrayType {
        TODO("Not yet implemented")
    }

    override fun withStaticTyping(): ArrayType {
        TODO("Not yet implemented")
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings, superClass: KotlinType,
            superInterfaces: Array<KotlinType>): ArrayType? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    override val isContainerType = false

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
        TODO("Not yet implemented")
    }

    override fun getErasedSignature(stringBuilder: StringBuilder): StringBuilder {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

}