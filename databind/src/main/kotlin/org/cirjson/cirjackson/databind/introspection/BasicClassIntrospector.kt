package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig

open class BasicClassIntrospector : ClassIntrospector {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor() : super() {
    }

    override fun forMapper(): ClassIntrospector {
        TODO("Not yet implemented")
    }

    override fun forOperation(config: MapperConfig<*>): BasicClassIntrospector {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Factory method implementations: annotation resolution
     *******************************************************************************************************************
     */

    override fun introspectClassAnnotations(type: KotlinType): AnnotatedClass {
        TODO("Not yet implemented")
    }

    override fun introspectDirectClassAnnotations(type: KotlinType): AnnotatedClass {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Factory method implementations: bean introspection
     *******************************************************************************************************************
     */

    override fun introspectForSerialization(type: KotlinType): BeanDescription {
        TODO("Not yet implemented")
    }

    override fun introspectForDeserialization(type: KotlinType): BeanDescription {
        TODO("Not yet implemented")
    }

    override fun introspectForDeserializationWithBuilder(type: KotlinType,
            valueTypeDescription: BeanDescription): BeanDescription {
        TODO("Not yet implemented")
    }

    override fun introspectForCreation(type: KotlinType): BeanDescription {
        TODO("Not yet implemented")
    }

}