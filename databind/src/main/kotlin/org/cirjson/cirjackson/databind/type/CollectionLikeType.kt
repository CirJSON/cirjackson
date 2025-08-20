package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * Type that represents things that act similar to [Collection]; but may or may not be instances of that interface. This
 * specifically allows framework to check for configuration and annotation settings used for Map types, and pass these
 * to custom handlers that may be more familiar with actual type.
 */
@Suppress("EqualsOrHashCode")
open class CollectionLikeType : TypeBase {

    protected val myElementType: KotlinType

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(collectionType: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            interfaces: Array<KotlinType>?, elementType: KotlinType, valueHandler: Any?, typeHandler: Any?,
            isUsedAsStaticType: Boolean) : super(collectionType, bindings, superClass, interfaces,
            elementType.hashCode(), valueHandler, typeHandler, isUsedAsStaticType) {
        myElementType = elementType
    }

    protected constructor(base: TypeBase, contentType: KotlinType) : super(base) {
        myElementType = contentType
    }

    override fun withContentType(contentType: KotlinType): KotlinType {
        if (myElementType === contentType) {
            return this
        }

        return CollectionLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, contentType, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withTypeHandler(handler: Any?): CollectionLikeType {
        return CollectionLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, myElementType, myValueHandler,
                handler, isUsedAsStaticType)
    }

    override fun withContentTypeHandler(handler: Any?): CollectionLikeType {
        return CollectionLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces,
                myElementType.withTypeHandler(handler), myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withValueHandler(handler: Any?): CollectionLikeType {
        return CollectionLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, myElementType, handler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withContentValueHandler(handler: Any?): CollectionLikeType {
        return CollectionLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces,
                myElementType.withValueHandler(handler), myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withHandlersFrom(src: KotlinType): KotlinType {
        val type = super.withHandlersFrom(src)
        val sourceContentType = src.contentType ?: return type
        val contentType = myElementType.withHandlersFrom(sourceContentType)

        return type.takeUnless { contentType === myElementType } ?: type.withContentType(contentType)
    }

    override fun withStaticTyping(): CollectionLikeType {
        if (isUsedAsStaticType) {
            return this
        }

        return CollectionLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces,
                myElementType.withStaticTyping(), myValueHandler, myTypeHandler, true)
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        return CollectionLikeType(raw, bindings, superClass, superInterfaces, myElementType, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    override val isContainerType = true

    override val isCollectionLikeType: Boolean = true

    override val contentType: KotlinType
        get() = myElementType

    override val contentValueHandler: Any?
        get() = myElementType.valueHandler

    override val contentTypeHandler: Any?
        get() = myElementType.typeHandler

    override fun hasHandlers(): Boolean {
        return super.hasHandlers() || myElementType.hasHandlers()
    }

    override fun getErasedSignature(stringBuilder: StringBuilder): StringBuilder {
        return classSignature(myClass, stringBuilder, true)
    }

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
        classSignature(myClass, stringBuilder, false)
        stringBuilder.append('<')
        myElementType.getGenericSignature(stringBuilder)
        stringBuilder.append(">;")
        return stringBuilder
    }

    override fun buildCanonicalName(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(myClass.qualifiedName)

        if (hasNTypeParameters(1)) {
            stringBuilder.append('<')
            stringBuilder.append(myElementType.toCanonical())
            stringBuilder.append('>')
        }

        return stringBuilder.toString()
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "[collection-like type; class ${myClass.qualifiedName}, contains $myElementType]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is CollectionLikeType || other::class != this::class) {
            return false
        }

        return myClass == other.myClass && myElementType == other.myElementType
    }

    companion object {

        fun construct(collectionType: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
                interfaces: Array<KotlinType>?, elementType: KotlinType): CollectionLikeType {
            return CollectionLikeType(collectionType, bindings, superClass, interfaces, elementType, null, null, false)
        }

        /**
         * Factory method that can be used to "upgrade" a basic type into collection-like one; usually done via
         * [TypeModifier].
         */
        fun upgradeFrom(baseType: KotlinType, elementType: KotlinType): CollectionLikeType {
            if (baseType !is TypeBase) {
                throw IllegalArgumentException("Cannot upgrade from an instance of ${baseType::class}")
            }

            return CollectionLikeType(baseType, elementType)
        }

    }

}