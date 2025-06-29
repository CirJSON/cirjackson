package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.databind.AnnotationIntrospector

open class AnnotationIntrospectorPair(private val myPrimary: AnnotationIntrospector,
        private val mySecondary: AnnotationIntrospector) : AnnotationIntrospector() {

    override fun version(): Version {
        TODO("Not yet implemented")
    }

    companion object {

        fun create(primary: AnnotationIntrospector?, secondary: AnnotationIntrospector?): AnnotationIntrospectorPair {
            TODO("Not yet implemented")
        }

    }

}