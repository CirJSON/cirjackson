package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor
import org.cirjson.cirjackson.databind.introspection.ConcreteBeanPropertyBase
import kotlin.reflect.KClass

abstract class SettableBeanProperty : ConcreteBeanPropertyBase {

    /*
     *******************************************************************************************************************
     * Lifecycle (construct & configure)
     *******************************************************************************************************************
     */

    protected constructor(source: SettableBeanProperty) : super(source) {
    }

    /*
     *******************************************************************************************************************
     * BeanProperty implementation
     *******************************************************************************************************************
     */

    override val name: String
        get() = TODO("Not yet implemented")

    override val fullName: PropertyName
        get() = TODO("Not yet implemented")

    override val type: KotlinType
        get() = TODO("Not yet implemented")

    override val wrapperName: PropertyName?
        get() = TODO("Not yet implemented")

    override fun <A : Annotation> getContextAnnotation(clazz: KClass<A>): A? {
        TODO("Not yet implemented")
    }

    override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider) {
        TODO("Not yet implemented")
    }

}