package org.cirjson.cirjackson.databind.external.beans

import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.introspection.AnnotatedParameter
import java.beans.ConstructorProperties
import java.beans.Transient
import kotlin.reflect.KClass

class JavaBeansAnnotationsImplementation : JavaBeansAnnotations() {

    private val myBogus: KClass<*>

    init {
        @Suppress("unused")
        val clazz = Transient::class
        myBogus = ConstructorProperties::class
    }

    override fun findTransient(annotated: Annotated): Boolean? {
        return annotated.getAnnotation(Transient::class)?.value
    }

    override fun hasCreatorAnnotation(annotated: Annotated): Boolean? {
        val props = annotated.getAnnotation(ConstructorProperties::class)
        return (props != null).takeIf { it }
    }

    override fun findConstructorName(parameter: AnnotatedParameter): PropertyName? {
        val constructor = parameter.owner
        val props = constructor.getAnnotation(ConstructorProperties::class) ?: return null
        val names = props.value
        val index = parameter.index

        if (index >= names.size) {
            return null
        }

        return PropertyName.construct(names[index])
    }

}