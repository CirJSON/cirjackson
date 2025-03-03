package org.cirjson.cirjackson.annotations

import kotlin.reflect.KClass

/**
 * Marker interface used by value classes like [CirJsonFormat.Value] that are used to contain information from one of
 * CirJackson annotations, and can be directly instantiated from those annotations, as well as programmatically
 * constructed and possibly merged. The reason for such marker is to allow generic handling of some of the annotations,
 * as well as to allow easier injection of configuration from sources other than annotations.
 */
interface CirJacksonAnnotationValue<A : Annotation> {

    /**
     * Introspection method that may be used to find actual annotation that may be used as the source for value
     * instance.
     *
     * @return Annotation class for which instances of this value class are created
     */
    fun valueFor(): KClass<A>

}