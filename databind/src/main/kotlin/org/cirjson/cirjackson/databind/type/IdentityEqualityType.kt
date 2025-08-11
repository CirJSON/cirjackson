package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

abstract class IdentityEqualityType protected constructor(raw: KClass<*>, bindings: TypeBindings?,
        superClass: KotlinType?, interfaces: List<KotlinType>?, additionalHash: Int, valueHandler: Any?,
        typeHandler: Any?, isUsedAsStaticType: Boolean) :
        TypeBase(raw, bindings, superClass, interfaces, additionalHash, valueHandler, typeHandler, isUsedAsStaticType) {

    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

    override fun hashCode(): Int {
        TODO("Not yet implemented")
    }

}