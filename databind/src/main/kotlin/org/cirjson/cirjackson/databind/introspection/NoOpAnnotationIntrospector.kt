package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.databind.AnnotationIntrospector

abstract class NoOpAnnotationIntrospector : AnnotationIntrospector() {

    override fun version(): Version {
        TODO("Not yet implemented")
    }

    companion object {

        val INSTANCE = object : NoOpAnnotationIntrospector() {
        }

    }

}