package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType

class CollectionType : CollectionLikeType {

    private constructor(base: TypeBase, contentType: KotlinType) : super(base, contentType)

}