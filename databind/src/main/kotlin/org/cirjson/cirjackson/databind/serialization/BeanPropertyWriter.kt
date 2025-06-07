package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyMetadata
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import kotlin.reflect.KClass

open class BeanPropertyWriter : PropertyWriter {

    protected constructor() : super(PropertyMetadata.STANDARD_REQUIRED_OR_OPTIONAL) {
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

    override fun <A : Annotation> getAnnotation(clazz: KClass<A>): A? {
        TODO("Not yet implemented")
    }

    override fun <A : Annotation> getContextAnnotation(clazz: KClass<A>): A? {
        TODO("Not yet implemented")
    }

    override val member: AnnotatedMember?
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * PropertyWriter methods (schema generation)
     *******************************************************************************************************************
     */

    override fun depositSchemaProperty(objectVisitor: CirJsonObjectFormatVisitor, provider: SerializerProvider) {
        TODO("Not yet implemented")
    }

}