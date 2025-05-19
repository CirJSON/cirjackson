package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.core.Versioned
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.Annotated

abstract class AnnotationIntrospector : Versioned {

    /*
     *******************************************************************************************************************
     * General member (field, method/constructor) annotations
     *******************************************************************************************************************
     */

    open fun findFormat(config: MapperConfig<*>, annotated: Annotated): CirJsonFormat.Value? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Serialization: general annotations
     *******************************************************************************************************************
     */

    open fun findPropertyInclusion(config: MapperConfig<*>, annotated: Annotated): CirJsonInclude.Value? {
        TODO("Not yet implemented")
    }

}