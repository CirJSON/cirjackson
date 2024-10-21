package org.cirjson.cirjackson.annotations

/**
 * Meta-annotation (annotations used on other annotations) used for marking all annotations that are part of CirJackson
 * package. Can be used for recognizing all CirJackson annotations generically, and in future also for passing other
 * generic annotation configuration.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CirJacksonAnnotation
