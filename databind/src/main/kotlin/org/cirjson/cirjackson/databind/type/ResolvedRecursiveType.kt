package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

open class ResolvedRecursiveType(erasedType: KClass<*>, bindings: TypeBindings?) :
        IdentityEqualityType(erasedType, bindings, null, null, 0, null, null, false) {

    var selfReferencedType: KotlinType? = null
        set(value) {
            TODO("Not yet implemented")
        }

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
        TODO("Not yet implemented")
    }

    override fun getErasedSignature(stringBuilder: StringBuilder): StringBuilder {
        TODO("Not yet implemented")
    }

    override fun withContentType(contentType: KotlinType): KotlinType {
        TODO("Not yet implemented")
    }

    override fun withTypeHandler(handler: Any?): KotlinType {
        TODO("Not yet implemented")
    }

    override fun withContentTypeHandler(handler: Any?): KotlinType {
        TODO("Not yet implemented")
    }

    override fun withValueHandler(handler: Any?): KotlinType {
        TODO("Not yet implemented")
    }

    override fun withContentValueHandler(handler: Any?): KotlinType {
        TODO("Not yet implemented")
    }

    override fun withStaticTyping(): KotlinType {
        TODO("Not yet implemented")
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings, superClass: KotlinType,
            superInterfaces: Array<KotlinType>): KotlinType? {
        TODO("Not yet implemented")
    }

    override val isContainerType: Boolean
        get() = TODO("Not yet implemented")

    override fun toString(): String {
        TODO("Not yet implemented")
    }

}