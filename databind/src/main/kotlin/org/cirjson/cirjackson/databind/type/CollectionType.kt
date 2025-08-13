package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

class CollectionType private constructor(collectionType: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
        interfaces: Array<KotlinType>?, elementType: KotlinType, valueHandler: Any?, typeHandler: Any?,
        isUsedAsStaticType: Boolean) :
        CollectionLikeType(collectionType, bindings, superClass, interfaces, elementType, valueHandler, typeHandler,
                isUsedAsStaticType) {
}