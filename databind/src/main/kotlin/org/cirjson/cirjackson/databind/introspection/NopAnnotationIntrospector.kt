package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.configuration.PackageVersion

/**
 * Dummy, "no-operation" implementation of [AnnotationIntrospector]. Can be used as is to suppress handling of
 * annotations; or as a basis for simple configuration overrides (whether based on annotations or not).
 */
abstract class NopAnnotationIntrospector : AnnotationIntrospector() {

    override fun version(): Version {
        return Version.unknownVersion()
    }

    companion object {

        /**
         * Static immutable and shareable instance that can be used as "null" introspector: one that never finds any
         * annotation information.
         */
        val INSTANCE = object : NopAnnotationIntrospector() {

            override fun version(): Version {
                return PackageVersion.VERSION
            }

        }

    }

}