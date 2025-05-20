package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.databind.PropertyMetadata
import org.cirjson.cirjackson.databind.introspection.ConcreteBeanPropertyBase

abstract class PropertyWriter : ConcreteBeanPropertyBase {

    protected constructor(metadata: PropertyMetadata?) : super(metadata)

}