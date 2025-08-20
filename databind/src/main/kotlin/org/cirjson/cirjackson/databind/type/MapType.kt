package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * Type that represents "true" Map types.
 */
class MapType private constructor(mapType: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
        interfaces: Array<KotlinType>?, keyType: KotlinType, valueType: KotlinType, valueHandler: Any?,
        typeHandler: Any?, isUsedAsStaticType: Boolean) :
        MapLikeType(mapType, bindings, superClass, interfaces, keyType, valueType, valueHandler, typeHandler,
                isUsedAsStaticType) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun withKeyType(keyType: KotlinType): MapType {
        if (myKeyType === keyType) {
            return this
        }

        return MapType(myClass, myBindings, mySuperClass, mySuperInterfaces, keyType, myValueType, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withContentType(contentType: KotlinType): KotlinType {
        if (myValueType === contentType) {
            return this
        }

        return MapType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType, contentType, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withTypeHandler(handler: Any?): MapType {
        return MapType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType, myValueType, myValueHandler,
                handler, isUsedAsStaticType)
    }

    override fun withContentTypeHandler(handler: Any?): MapType {
        return MapType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType,
                myValueType.withTypeHandler(handler), myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withValueHandler(handler: Any?): MapType {
        return MapType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType, myValueType, handler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withContentValueHandler(handler: Any?): MapType {
        return MapType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType,
                myValueType.withValueHandler(handler), myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withStaticTyping(): MapType {
        if (isUsedAsStaticType) {
            return this
        }

        return MapType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType.withStaticTyping(),
                myValueType.withStaticTyping(), myValueHandler, myTypeHandler, true)
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        return MapType(raw, bindings, superClass, superInterfaces, myKeyType, myValueType, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }
    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    override fun withKeyTypeHandler(handler: Any?): MapType {
        return MapType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType.withTypeHandler(handler),
                myValueType, myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withKeyValueHandler(handler: Any?): MapType {
        return MapType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType.withValueHandler(handler),
                myValueType, myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "[map type; class ${myClass.qualifiedName}, $myKeyType -> $myValueType]"
    }

    companion object {

        fun construct(mapType: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
                interfaces: Array<KotlinType>?, keyType: KotlinType, valueType: KotlinType): MapType {
            return MapType(mapType, bindings, superClass, interfaces, keyType, valueType, null, null, false)
        }

    }

}