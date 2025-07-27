package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.DefaultTyping
import org.cirjson.cirjackson.databind.cirjsontype.PolymorphicTypeValidator

open class DefaultTypeResolverBuilder : StandardTypeResolverBuilder {

    constructor(subtypeValidator: PolymorphicTypeValidator, typing: DefaultTyping,
            includeAs: CirJsonTypeInfo.As) : super() {
    }

    constructor(subtypeValidator: PolymorphicTypeValidator, typing: DefaultTyping, propertyName: String?) : super() {
    }

}