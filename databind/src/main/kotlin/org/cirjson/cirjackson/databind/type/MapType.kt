package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

class MapType private constructor(mapType: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
        interfaces: Array<KotlinType>?, keyType: KotlinType, valueType: KotlinType, valueHandler: Any?,
        typeHandler: Any?, isUsedAsStaticType: Boolean) :
        MapLikeType(mapType, bindings, superClass, interfaces, keyType, valueType, valueHandler, typeHandler,
                isUsedAsStaticType) {
}