package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * Simple types are defined as anything other than one of recognized container types (arrays, Collections, Maps). For
 * our needs we need not know anything further, since we have no way of dealing with generic types other than
 * Collections and Maps.
 */
@Suppress("EqualsOrHashCode")
open class SimpleType : TypeBase {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Constructor only used by core CirJackson databind functionality; should never be called by application code.
     *
     * As with other direct construction that by-passes [TypeFactory], no introspection occurs with respect to
     * super-types; caller must be aware of consequences if using this method.
     */
    protected constructor(clazz: KClass<*>) : this(clazz, TypeBindings.EMPTY, null, null)

    protected constructor(clazz: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?) : this(clazz, bindings, superClass, superInterfaces, null, null, false)

    /**
     * Simple copy-constructor, usually used when upgrading/refining a simple type into more specialized type.
     */
    protected constructor(base: TypeBase) : super(base)

    protected constructor(clazz: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?, valueHandler: Any?, typeHandler: Any?,
            isUsedAsStaticType: Boolean) : super(clazz, bindings, superClass, superInterfaces,
            (bindings ?: TypeBindings.EMPTY).hashCode(), valueHandler, typeHandler, isUsedAsStaticType)

    protected constructor(clazz: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?, extraHash: Int, valueHandler: Any?, typeHandler: Any?,
            isUsedAsStaticType: Boolean) : super(clazz, bindings, superClass, superInterfaces, extraHash, valueHandler,
            typeHandler, isUsedAsStaticType)

    override fun withContentType(contentType: KotlinType): KotlinType {
        throw IllegalArgumentException("Simple types have no content types; cannot call withContentType()")
    }

    override fun withTypeHandler(handler: Any?): SimpleType {
        if (myTypeHandler === handler) {
            return this
        }

        return SimpleType(myClass, myBindings, mySuperClass, mySuperInterfaces, myValueHandler, handler,
                isUsedAsStaticType)
    }

    override fun withContentTypeHandler(handler: Any?): KotlinType {
        throw IllegalArgumentException("Simple types have no content types; cannot call withContentTypeHandler()")
    }

    override fun withValueHandler(handler: Any?): SimpleType {
        if (myValueHandler === handler) {
            return this
        }

        return SimpleType(myClass, myBindings, mySuperClass, mySuperInterfaces, handler, myTypeHandler,
                isUsedAsStaticType)
    }

    override fun withContentValueHandler(handler: Any?): SimpleType {
        throw IllegalArgumentException("Simple types have no content types; cannot call withContentValueHandler()")
    }

    override fun withStaticTyping(): SimpleType {
        if (isUsedAsStaticType) {
            return this
        }

        return SimpleType(myClass, myBindings, mySuperClass, mySuperInterfaces, myValueHandler, myTypeHandler, true)
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings, superClass: KotlinType,
            superInterfaces: Array<KotlinType>): KotlinType? {
        return null
    }

    override fun buildCanonicalName(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(myClass.qualifiedName)

        val count = myBindings.size

        if (count > 0 && hasNTypeParameters(count)) {
            stringBuilder.append('<')

            for (i in 0..<count) {
                val type = containedType(i)!!

                if (i > 0) {
                    stringBuilder.append(',')
                }

                stringBuilder.append(type.toCanonical())
            }

            stringBuilder.append('>')
        }

        return stringBuilder.toString()
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    override val isContainerType = false

    override fun hasContentType(): Boolean {
        return false
    }

    override fun getErasedSignature(stringBuilder: StringBuilder): StringBuilder {
        return classSignature(myClass, stringBuilder, true)
    }

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
        classSignature(myClass, stringBuilder, false)

        val count = myBindings.size

        if (count > 0) {
            stringBuilder.append('<')

            for (i in 0..<count) {
                containedType(i)!!.getErasedSignature(stringBuilder)
            }

            stringBuilder.append('>')
        }

        stringBuilder.append(';')
        return stringBuilder
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        val stringBuilder = StringBuilder(40)
        stringBuilder.append("[simple type, class ").append(buildCanonicalName()).append(']')
        return stringBuilder.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is SimpleType || this::class != other::class) {
            return false
        }

        return myClass == other.myClass && myBindings == other.myBindings
    }

    companion object {

        /**
         * Method used by core Jackson classes: NOT to be used by application code: it does NOT properly handle
         * inspection of super-types, so neither parent Classes nor implemented Interfaces are accessible with resulting
         * type instance.
         *
         * NOTE: public only because it is called by `ObjectMapper` which is not in same package
         */
        fun constructUnsafe(raw: KClass<*>): SimpleType {
            return SimpleType(raw, null, null, null, null, null, false)
        }

    }

}