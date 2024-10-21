package org.cirjson.cirjackson.annotations

/**
 * Meta-annotation (annotations used on other annotations) used for indicating that instead of using target annotation
 * (annotation annotated with this annotation), CirJackson should use meta-annotations it has. This can be useful in
 * creating "combo-annotations" by having a container annotation, which needs to be annotated with this annotation as
 * well as all annotations it 'contains'.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJacksonAnnotationsInside
