package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJsonCreator
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.configuration.MapperConfig

open class PotentialCreator(private val myCreator: AnnotatedWithParams,
        private val myCreatorMode: CirJsonCreator.Mode?) {

    /*
     *******************************************************************************************************************
     * Mutators
     *******************************************************************************************************************
     */

    open fun introspectParameterNames(config: MapperConfig<*>, implicits: Array<PropertyName>): PotentialCreator {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open fun creator(): AnnotatedWithParams {
        TODO("Not yet implemented")
    }

    open fun parameterCount(): Int {
        TODO("Not yet implemented")
    }

}