package org.cirjson.cirjackson.databind.external.beans

import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.introspection.AnnotatedParameter
import org.cirjson.cirjackson.databind.util.createInstance
import org.cirjson.cirjackson.databind.util.rethrowIfFatal

/**
 * Since two JDK7-added annotations were left out of JDK 9+ core modules, moved into "java.beans", support for them will
 * be left as dynamic, and handled via this class
 */
abstract class JavaBeansAnnotations {

    abstract fun findTransient(annotated: Annotated): Boolean?

    abstract fun hasCreatorAnnotation(annotated: Annotated): Boolean?

    abstract fun findConstructorName(parameter: AnnotatedParameter): PropertyName?

    companion object {

        val IMPLEMENTATION = Class.forName(
                "org.cirjson.cirjackson.databind.external.beans.JavaBeansAnnotationsImplementation").kotlin.let {
            try {
                it.createInstance(false) as JavaBeansAnnotations
            } catch (e: IllegalAccessException) {
                null
            } catch (t: Throwable) {
                t.rethrowIfFatal()
                null
            }
        }

    }

}