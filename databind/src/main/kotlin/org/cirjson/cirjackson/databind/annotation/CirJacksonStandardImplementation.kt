package org.cirjson.cirjackson.databind.annotation

import org.cirjson.cirjackson.annotations.CirJacksonAnnotation

/**
 * Marker interface used to indicate implementation classes (serializers, deserializers, etc.) that are standard ones
 * CirJackson uses; not custom ones that application has added. It can be added in cases where certain optimizations can
 * be made if default instances are uses; for example when handling conversions of "natural" CirJSON types like Strings,
 * booleans and numbers.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJacksonStandardImplementation
