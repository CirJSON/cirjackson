package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.TypeDeserializer
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember

abstract class SettableAnyProperty(protected val myProperty: BeanProperty, protected val mySetter: AnnotatedMember?,
        protected val myType: KotlinType, protected val myKeyDeserializer: KeyDeserializer?,
        protected val myValueDeserializer: ValueDeserializer<Any>?,
        protected val myValueTypeDeserializer: TypeDeserializer?) {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    abstract fun withValueDeserializer(deserializer: ValueDeserializer<Any>?): SettableAnyProperty

    /*
     *******************************************************************************************************************
     * Public API, accessors
     *******************************************************************************************************************
     */

    open val property: BeanProperty
        get() = myProperty

    open fun hasValueDeserializer(): Boolean {
        TODO("Not yet implemented")
    }

    open val type: KotlinType
        get() = myType

    /*
     *******************************************************************************************************************
     * Public API, deserialization
     *******************************************************************************************************************
     */

    open fun deserializeAndSet(parser: CirJsonParser, context: DeserializationContext, instance: Any,
            propertyName: String) {
        TODO("Not yet implemented")
    }

}