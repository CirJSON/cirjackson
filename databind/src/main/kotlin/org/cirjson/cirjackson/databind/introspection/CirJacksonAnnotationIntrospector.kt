package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.*
import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.annotation.CirJsonDeserialize
import org.cirjson.cirjackson.databind.annotation.CirJsonSerialize
import org.cirjson.cirjackson.databind.external.beans.JavaBeansAnnotations
import org.cirjson.cirjackson.databind.util.LookupCache
import org.cirjson.cirjackson.databind.util.SimpleLookupCache
import org.cirjson.cirjackson.databind.util.rethrowIfFatal

/**
 * [AnnotationIntrospector] implementation that handles standard CirJackson annotations.
 */
open class CirJacksonAnnotationIntrospector : AnnotationIntrospector() {

    /**
     * Since introspection of annotation types is a performance issue in some use cases (rare, but do exist), let's try
     * a simple cache to reduce need for actual meta-annotation introspection.
     *
     * Non-final only because it needs to be recreated after deserialization.
     */
    @Transient
    protected var myAnnotationsInside: LookupCache<String, Boolean>? = SimpleLookupCache(48, 96)

    /*
     *******************************************************************************************************************
     * Local configuration settings
     *******************************************************************************************************************
     */

    /**
     * See [withConstructorPropertiesImpliesCreator] for explanation.
     *
     * Defaults to `true`.
     */
    protected var myConfigConstructorPropertiesImpliesCreator = true

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun version(): Version {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    /**
     * Method for changing behavior of [java.beans.ConstructorProperties]: if set to `true`, existence DOES indicate
     * that the given constructor should be considered a creator; `false`, that it should NOT be considered a creator
     * without explicit use of `CirJsonCreator` annotation.
     *
     * Default setting is `true`
     */
    open fun withConstructorPropertiesImpliesCreator(b: Boolean): CirJacksonAnnotationIntrospector {
        myConfigConstructorPropertiesImpliesCreator = b
        return this
    }

    companion object {

        private val ANNOTATIONS_TO_INFER_SERIALIZATION =
                arrayOf(CirJsonSerialize::class, CirJsonView::class, CirJsonFormat::class, CirJsonTypeInfo::class,
                        CirJsonRawValue::class, CirJsonUnwrapped::class, CirJsonBackReference::class,
                        CirJsonManagedReference::class)

        private val ANNOTATIONS_TO_INFER_DESERIALIZATION =
                arrayOf(CirJsonDeserialize::class, CirJsonView::class, CirJsonFormat::class, CirJsonTypeInfo::class,
                        CirJsonUnwrapped::class, CirJsonBackReference::class, CirJsonManagedReference::class,
                        CirJsonMerge::class)

        private val BEANS_HELPER = try {
            JavaBeansAnnotations.IMPLEMENTATION
        } catch (t: Throwable) {
            t.rethrowIfFatal()
            null
        }

    }

}