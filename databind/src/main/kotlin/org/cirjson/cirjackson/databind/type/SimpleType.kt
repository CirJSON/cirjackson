package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

open class SimpleType : TypeBase {

    protected constructor(base: TypeBase) : super(base)

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withContentType(contentType: KotlinType): KotlinType {
        TODO("Not yet implemented")
    }

    override fun withTypeHandler(handler: Any?): SimpleType {
        TODO("Not yet implemented")
    }

    override fun withContentTypeHandler(handler: Any?): KotlinType {
        TODO("Not yet implemented")
    }

    override fun withValueHandler(handler: Any?): SimpleType {
        TODO("Not yet implemented")
    }

    override fun withContentValueHandler(handler: Any?): KotlinType {
        TODO("Not yet implemented")
    }

    override fun withStaticTyping(): SimpleType {
        TODO("Not yet implemented")
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings, superClass: KotlinType,
            superInterfaces: Array<KotlinType>): KotlinType? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    override val isContainerType = false

    override fun getErasedSignature(stringBuilder: StringBuilder): StringBuilder {
        TODO("Not yet implemented")
    }

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
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