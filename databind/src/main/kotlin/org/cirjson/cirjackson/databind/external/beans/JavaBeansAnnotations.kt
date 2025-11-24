package org.cirjson.cirjackson.databind.external.beans

import org.cirjson.cirjackson.databind.util.createInstance
import org.cirjson.cirjackson.databind.util.rethrowIfFatal

abstract class JavaBeansAnnotations {

    companion object {

        val IMPLEMENTATION = Class.forName("tools.jackson.databind.ext.beans.JavaBeansAnnotationsImpl").kotlin.let {
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