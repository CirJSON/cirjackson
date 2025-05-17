package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType

class MapType : MapLikeType {

    private constructor(base: TypeBase, keyType: KotlinType, contentType: KotlinType) : super(base, keyType,
            contentType)

}