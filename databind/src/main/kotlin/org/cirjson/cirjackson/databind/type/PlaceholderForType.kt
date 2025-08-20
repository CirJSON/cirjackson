package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * Helper type used when introspecting bindings for already resolved types, needed for specialization.
 */
open class PlaceholderForType(protected val myOrdinal: Int) :
        IdentityEqualityType(Any::class, TypeBindings.EMPTY, TypeFactory.unknownType(), null, 1, null, null, false) {

    /**
     * Type assigned during wildcard resolution (which follows type structure resolution)
     */
    protected var myActualType: KotlinType? = null

    open fun actualType(): KotlinType? {
        return myActualType
    }

    open fun actualType(type: KotlinType?) {
        myActualType = type
    }

    override fun buildCanonicalName(): String {
        return toString()
    }

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
        return getErasedSignature(stringBuilder)
    }

    override fun getErasedSignature(stringBuilder: StringBuilder): StringBuilder {
        return stringBuilder.append('$').append(myOrdinal + 1)
    }

    override fun withContentType(contentType: KotlinType): KotlinType {
        return unsupported()
    }

    override fun withStaticTyping(): KotlinType {
        return unsupported()
    }

    override fun withTypeHandler(handler: Any?): KotlinType {
        return unsupported()
    }

    override fun withContentTypeHandler(handler: Any?): KotlinType {
        return unsupported()
    }

    override fun withValueHandler(handler: Any?): KotlinType {
        return unsupported()
    }

    override fun withContentValueHandler(handler: Any?): KotlinType {
        return unsupported()
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType? {
        return unsupported()
    }

    override val isContainerType: Boolean
        get() = false

    override fun toString(): String {
        return getErasedSignature(StringBuilder()).toString()
    }

    private fun <T> unsupported(): T {
        throw UnsupportedOperationException("Operation should not be attempted on ${this::class.qualifiedName}")
    }

}