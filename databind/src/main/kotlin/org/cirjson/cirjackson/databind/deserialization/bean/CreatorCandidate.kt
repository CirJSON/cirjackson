package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.annotations.CirJacksonInject
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedParameter
import org.cirjson.cirjackson.databind.introspection.AnnotatedWithParams
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition

class CreatorCandidate private constructor(private val myCreator: AnnotatedWithParams,
        private val myParameters: Array<Parameter>, private val myParameterCount: Int) {

    fun creator(): AnnotatedWithParams {
        return myCreator
    }

    fun parameterCount(): Int {
        return myParameterCount
    }

    fun injection(i: Int): CirJacksonInject.Value? {
        return myParameters[i].injection
    }

    fun parameter(i: Int): AnnotatedParameter? {
        return myParameters[i].annotated
    }

    fun propertyDefinition(i: Int): BeanPropertyDefinition? {
        return myParameters[i].propertyDefinition
    }

    fun parameterName(i: Int): PropertyName? {
        return myParameters[i].fullName()
    }

    fun explicitParameterName(i: Int): PropertyName? {
        return myParameters[i].propertyDefinition?.takeIf { it.isExplicitlyNamed }?.fullName
    }

    override fun toString(): String {
        return myCreator.toString()
    }

    class Parameter(val annotated: AnnotatedParameter, val propertyDefinition: BeanPropertyDefinition?,
            val injection: CirJacksonInject.Value?) {

        fun fullName(): PropertyName? {
            return propertyDefinition?.fullName
        }

        fun hasFullName(): Boolean {
            val name = propertyDefinition?.fullName ?: return false
            return name.hasSimpleName()
        }

    }

    companion object {

        fun construct(config: MapperConfig<*>, creator: AnnotatedWithParams,
                propertyDefinitions: Array<BeanPropertyDefinition>?): CreatorCandidate {
            val introspector = config.annotationIntrospector!!
            val parameterCount = creator.parameterCount

            val parameters = Array(parameterCount) { i ->
                val annotatedParameter = creator.getParameter(i)
                val injectId = introspector.findInjectableValue(config, annotatedParameter)
                Parameter(annotatedParameter, propertyDefinitions?.get(i), injectId)
            }

            return CreatorCandidate(creator, parameters, parameterCount)
        }

    }

}