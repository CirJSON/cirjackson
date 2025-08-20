package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * Type that represents Kotlin Collection types (Lists, Sets).
 */
class CollectionType private constructor(collectionType: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
        interfaces: Array<KotlinType>?, elementType: KotlinType, valueHandler: Any?, typeHandler: Any?,
        isUsedAsStaticType: Boolean) :
        CollectionLikeType(collectionType, bindings, superClass, interfaces, elementType, valueHandler, typeHandler,
                isUsedAsStaticType) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withContentType(contentType: KotlinType): KotlinType {
        if (myElementType === contentType) {
            return this
        }

        return CollectionType(myClass, myBindings, mySuperClass, mySuperInterfaces, contentType, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withTypeHandler(handler: Any?): CollectionType {
        return CollectionType(myClass, myBindings, mySuperClass, mySuperInterfaces, myElementType, myValueHandler,
                handler, isUsedAsStaticType)
    }

    override fun withContentTypeHandler(handler: Any?): CollectionType {
        return CollectionType(myClass, myBindings, mySuperClass, mySuperInterfaces,
                myElementType.withTypeHandler(handler), myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withValueHandler(handler: Any?): CollectionType {
        return CollectionType(myClass, myBindings, mySuperClass, mySuperInterfaces, myElementType, handler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withContentValueHandler(handler: Any?): CollectionType {
        return CollectionType(myClass, myBindings, mySuperClass, mySuperInterfaces,
                myElementType.withValueHandler(handler), myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withStaticTyping(): CollectionType {
        if (isUsedAsStaticType) {
            return this
        }

        return CollectionType(myClass, myBindings, mySuperClass, mySuperInterfaces, myElementType.withStaticTyping(),
                myValueHandler, myTypeHandler, true)
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        return CollectionType(raw, bindings, superClass, superInterfaces, myElementType, myValueHandler, myTypeHandler,
                isUsedAsStaticType)
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "[collection type; class ${myClass.qualifiedName}, contains $myElementType]"
    }

    companion object {

        fun construct(collectionType: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
                interfaces: Array<KotlinType>?, elementType: KotlinType): CollectionType {
            return CollectionType(collectionType, bindings, superClass, interfaces, elementType, null, null, false)
        }

    }

}