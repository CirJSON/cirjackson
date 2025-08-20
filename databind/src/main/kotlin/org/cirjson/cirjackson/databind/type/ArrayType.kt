package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * Array types represent Kotlin arrays, both primitive and object valued. Further, Object-valued arrays can have element
 * type of any other legal [KotlinType].
 *
 * @property myComponentType Type of elements in the array.
 *
 * @property myEmptyArray We will also keep track of shareable instance of empty array, since it usually needs to be
 * constructed anyway; and because it is essentially immutable and thus can be shared.
 */
@Suppress("EqualsOrHashCode")
class ArrayType private constructor(private val myComponentType: KotlinType, bindings: TypeBindings?,
        private val myEmptyArray: Any, valueHandler: Any?, typeHandler: Any?, isUsedAsStaticType: Boolean) :
        TypeBase(myEmptyArray::class, bindings, null, null, myComponentType.hashCode(), valueHandler, typeHandler,
                isUsedAsStaticType) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withContentType(contentType: KotlinType): KotlinType {
        val emptyInstance = java.lang.reflect.Array.newInstance(contentType.rawClass.java, 0)
        return ArrayType(contentType, myBindings, emptyInstance, myValueHandler, myTypeHandler, false)
    }

    override fun withTypeHandler(handler: Any?): ArrayType {
        if (handler === myTypeHandler) {
            return this
        }

        return ArrayType(myComponentType, myBindings, myEmptyArray, myValueHandler, handler, isUsedAsStaticType)
    }

    override fun withContentTypeHandler(handler: Any?): ArrayType {
        if (handler === myComponentType.typeHandler) {
            return this
        }

        return ArrayType(myComponentType.withTypeHandler(handler), myBindings, myEmptyArray, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withValueHandler(handler: Any?): ArrayType {
        if (handler === myValueHandler) {
            return this
        }

        return ArrayType(myComponentType, myBindings, myEmptyArray, handler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withContentValueHandler(handler: Any?): ArrayType {
        if (handler === myComponentType.valueHandler) {
            return this
        }

        return ArrayType(myComponentType.withValueHandler(handler), myBindings, myEmptyArray, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withStaticTyping(): ArrayType {
        if (isUsedAsStaticType) {
            return this
        }

        return ArrayType(myComponentType.withStaticTyping(), myBindings, myEmptyArray, myValueHandler, myTypeHandler,
                true)
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType? {
        return null
    }

    /*
     *******************************************************************************************************************
     * Overridden methods
     *******************************************************************************************************************
     */

    override val isArrayType: Boolean = true

    override val isAbstract: Boolean = false

    override val isConcrete: Boolean = true

    override val hasGenericTypes: Boolean
        get() = myComponentType.hasGenericTypes

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    override val isContainerType = false

    override val contentType: KotlinType
        get() = myComponentType

    override val contentValueHandler: Any?
        get() = myComponentType.valueHandler

    override val contentTypeHandler: Any?
        get() = myComponentType.typeHandler

    override fun hasHandlers(): Boolean {
        return super.hasHandlers() || myComponentType.hasHandlers()
    }

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
        stringBuilder.append('[')
        return myComponentType.getGenericSignature(stringBuilder)
    }

    override fun getErasedSignature(stringBuilder: StringBuilder): StringBuilder {
        stringBuilder.append('[')
        return myComponentType.getErasedSignature(stringBuilder)
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    val emptyArray: Array<Any?>
        get() = myEmptyArray as Array<Any?>

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "[array type, component type: $myComponentType]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is ArrayType) {
            return false
        }

        return myComponentType == other.myComponentType
    }

    companion object {

        fun construct(componentType: KotlinType, bindings: TypeBindings?): ArrayType {
            return construct(componentType, bindings, null, null)
        }

        fun construct(componentType: KotlinType, bindings: TypeBindings?, valueHandler: Any?,
                typeHandler: Any?): ArrayType {
            val emptyInstance = java.lang.reflect.Array.newInstance(componentType.rawClass.java, 0)
            return ArrayType(componentType, bindings, emptyInstance, valueHandler, typeHandler, false)
        }

    }

}