package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJsonCreator
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.configuration.MapperConfig

/**
 * Information about a single Creator (constructor or factory method), kept during property introspection.
 */
open class PotentialCreator(private val myCreator: AnnotatedWithParams,
        private val myCreatorMode: CirJsonCreator.Mode?) {

    private var myImplicitParameterNames: Array<PropertyName?>? = null

    private var myExplicitParameterNames: Array<PropertyName?>? = null

    /**
     * Parameter definitions if (and only if) this represents a Property-based Creator.
     */
    private var myPropertyDefinitions: List<BeanPropertyDefinition>? = null

    /*
     *******************************************************************************************************************
     * Mutators
     *******************************************************************************************************************
     */

    open fun assignPropertyDefinitions(propertyDefinitions: List<BeanPropertyDefinition>) {
        myPropertyDefinitions = propertyDefinitions
    }

    open fun introspectParameterNames(config: MapperConfig<*>): PotentialCreator {
        if (myImplicitParameterNames == null) {
            return this
        }

        val parameterCount = myCreator.parameterCount

        if (parameterCount == 0) {
            myExplicitParameterNames = NO_NAMES
            myImplicitParameterNames = NO_NAMES
            return this
        }

        myExplicitParameterNames = arrayOfNulls(parameterCount)
        myImplicitParameterNames = arrayOfNulls(parameterCount)

        val introspector = config.annotationIntrospector

        for (i in 0..<parameterCount) {
            val parameter = myCreator.getParameter(i)

            val rawImplicitName = introspector!!.findImplicitPropertyName(config, parameter)

            if (!rawImplicitName.isNullOrEmpty()) {
                myImplicitParameterNames!![i] = PropertyName.construct(rawImplicitName)
            }

            val explicitName = introspector.findNameForDeserialization(config, parameter) ?: continue

            if (!explicitName.isEmpty()) {
                myExplicitParameterNames!![i] = explicitName
            }
        }

        return this
    }

    open fun introspectParameterNames(config: MapperConfig<*>, implicits: Array<PropertyName?>): PotentialCreator {
        if (myImplicitParameterNames != null) {
            return this
        }

        val parameterCount = myCreator.parameterCount

        if (parameterCount == 0) {
            myExplicitParameterNames = NO_NAMES
            myImplicitParameterNames = NO_NAMES
            return this
        }

        myExplicitParameterNames = arrayOfNulls(parameterCount)
        myImplicitParameterNames = implicits

        val introspector = config.annotationIntrospector

        for (i in 0..<parameterCount) {
            val parameter = myCreator.getParameter(i)

            val explicitName = introspector!!.findNameForDeserialization(config, parameter) ?: continue

            if (!explicitName.isEmpty()) {
                myExplicitParameterNames!![i] = explicitName
            }
        }

        return this
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open fun creator(): AnnotatedWithParams {
        return myCreator
    }

    open fun creatorMode(): CirJsonCreator.Mode? {
        return myCreatorMode
    }

    open fun parameterCount(): Int {
        return myCreator.parameterCount
    }

    open fun parameter(index: Int): AnnotatedParameter {
        return myCreator.getParameter(index)
    }

    open fun hasExplicitNames(): Boolean {
        return myExplicitParameterNames!!.any { it != null }
    }

    open fun hasNameFor(index: Int): Boolean {
        return myExplicitParameterNames!![index] != null || myImplicitParameterNames!![index] != null
    }

    open fun hasNameOrInjectForAllParameters(config: MapperConfig<*>): Boolean {
        val introspector = config.annotationIntrospector

        for (i in myImplicitParameterNames!!.indices) {
            if (hasNameFor(i)) {
                continue
            }

            if (introspector?.findInjectableValue(config, myCreator.getParameter(i)) == null) {
                return false
            }
        }

        return true
    }

    open fun explicitName(index: Int): PropertyName? {
        return myExplicitParameterNames!![index]
    }

    open fun implicitName(index: Int): PropertyName? {
        return myImplicitParameterNames!![index]
    }

    open fun implicitNameSimple(index: Int): String? {
        return myImplicitParameterNames!![index]?.simpleName
    }

    open fun propertyDefinitions(): Array<BeanPropertyDefinition> {
        if (myPropertyDefinitions.isNullOrEmpty()) {
            return emptyArray()
        }

        return myPropertyDefinitions!!.toTypedArray()
    }

    /*
     *******************************************************************************************************************
     * Miscellaneous other
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "(mode=$myCreatorMode)$myCreator"
    }

    companion object {

        private val NO_NAMES = emptyArray<PropertyName?>()

    }

}