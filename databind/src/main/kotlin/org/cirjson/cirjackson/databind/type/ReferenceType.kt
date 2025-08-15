package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType

open class ReferenceType : SimpleType {

    protected constructor(base: TypeBase, type: KotlinType) : super(base) {
    }

}