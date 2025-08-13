package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * Internal abstract type representing [TypeBase] implementations which use reference equality.
 */
abstract class IdentityEqualityType protected constructor(raw: KClass<*>, bindings: TypeBindings?,
        superClass: KotlinType?, interfaces: Array<KotlinType>?, additionalHash: Int, valueHandler: Any?,
        typeHandler: Any?, isUsedAsStaticType: Boolean) :
        TypeBase(raw, bindings, superClass, interfaces, additionalHash, valueHandler, typeHandler, isUsedAsStaticType) {

    final override fun equals(other: Any?): Boolean {
        return other === this
    }

    override fun hashCode(): Int {
        return System.identityHashCode(this)
    }

}