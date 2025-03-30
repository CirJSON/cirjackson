package org.cirjson.cirjackson.databind.annotation

import org.cirjson.cirjackson.annotations.CirJacksonAnnotation

/**
 * Annotation used to configure details of a Builder class: instances of which are used as Builders for deserialized
 * POJO values, instead of POJOs being instantiated using constructors or factory methods. Note that this annotation is
 * NOT used to define what is the Builder class for a POJO: rather, this is determined by [CirJsonDeserialize.builder]
 * property of [CirJsonDeserialize].
 *
 * Annotation is typically used if the naming convention of a Builder class is different from defaults:
 *
 * * By default, setters are expected to have names like `withName` (for property `"name"`); override by [withPrefix]
 * property.
 *
 * In addition to configuration using this annotation, note that many other configuration annotations are also applied
 * to Builders, for example [org.cirjson.cirjackson.annotations.CirJsonIgnoreProperties] can be used to ignore "unknown"
 * properties.
 *
 * @property buildMethodName Property to use for re-defining which zero-argument method is considered the actual
 * "build-method": method called after all data has been bound, and the actual instance needs to be instantiated.
 *
 * Default value is `"build"`.
 *
 * @property withPrefix Property used for (re)defining name prefix to use for auto-detecting "with-methods": methods
 * that are similar to "set-methods" (in that they take an argument), but that may also return the new builder instance
 * to use (which may be 'this', or a new modified builder instance). Note that in addition to this prefix, it is also
 * possible to use [org.cirjson.cirjackson.annotations.CirJsonProperty] annotation to indicate "with-methods" (as well
 * as [org.cirjson.cirjackson.annotations.CirJsonSetter]).
 *
 * Default value is `"with"`, so that method named `"withValue()"` would be used for binding CirJSON property `"value"`
 * (using type indicated by the argument); or one defined with annotations.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonPOJOBuilder(val buildMethodName: String = DEFAULT_BUILD_METHOD,
        val withPrefix: String = DEFAULT_WITH_PREFIX) {

    /**
     * Simple value container for containing values read from [CirJsonPOJOBuilder] annotation instance.
     */
    class Value(val buildMethodName: String, val withPrefix: String) {

        constructor(annotation: CirJsonPOJOBuilder) : this(annotation.buildMethodName, annotation.withPrefix)

    }

    companion object {

        const val DEFAULT_BUILD_METHOD = "build"

        const val DEFAULT_WITH_PREFIX = "with"

    }

}
