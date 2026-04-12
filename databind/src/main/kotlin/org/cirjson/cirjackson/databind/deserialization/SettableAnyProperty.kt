package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.KeyDeserializer
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.ValueDeserializer
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember

abstract class SettableAnyProperty(protected val myProperty: BeanProperty, protected val mySetter: AnnotatedMember?,
        protected val myType: KotlinType, protected val myKeyDeserializer: KeyDeserializer?,
        protected val myValueDeserializer: ValueDeserializer<Any>?,
        protected val myValueTypeDeserializer: TypeDeserializer?) {
}