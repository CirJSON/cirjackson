package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

open class ResolvedRecursiveType(erasedType: KClass<*>, bindings: TypeBindings?) :
        IdentityEqualityType(erasedType, bindings, null, null, 0, null, null, false) {

    var selfReferencedType: KotlinType? = null
        set(value) {
            if (field != null) {
                throw IllegalStateException("Trying to re-set self reference; old value = $field, new = $value")
            }

            field = value!!
        }

    override val superClass: KotlinType?
        get() {
            if (selfReferencedType == null) {
                return super.superClass
            }

            return selfReferencedType!!.superClass
        }

    override val bindings: TypeBindings
        get() {
            if (selfReferencedType == null) {
                return super.bindings
            }

            return selfReferencedType!!.bindings
        }

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
        return selfReferencedType?.getErasedSignature(stringBuilder) ?: stringBuilder.append('?')
    }

    override fun getErasedSignature(stringBuilder: StringBuilder): StringBuilder {
        return selfReferencedType?.getErasedSignature(stringBuilder) ?: stringBuilder
    }

    override fun withContentType(contentType: KotlinType): KotlinType {
        return this
    }

    override fun withTypeHandler(handler: Any?): KotlinType {
        return this
    }

    override fun withContentTypeHandler(handler: Any?): KotlinType {
        return this
    }

    override fun withValueHandler(handler: Any?): KotlinType {
        return this
    }

    override fun withContentValueHandler(handler: Any?): KotlinType {
        return this
    }

    override fun withStaticTyping(): KotlinType {
        return this
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings, superClass: KotlinType,
            superInterfaces: Array<KotlinType>): KotlinType? {
        return null
    }

    override val isContainerType: Boolean
        get() = false

    override fun toString(): String {
        val stringBuilder = StringBuilder(40).append("[recursive type; ")
        stringBuilder.append(selfReferencedType?.rawClass?.qualifiedName ?: "UNRESOLVED")
        return stringBuilder.toString()
    }

}