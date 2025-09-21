package org.cirjson.cirjackson.databind.introspection

/**
 * Silly little "Pair" class needed for 2-element tuples (without adding dependency to one of 3rd party packages that
 * has one).
 */
class AnnotatedAndMetadata<A : Annotated, M : Any> private constructor(val annotated: A, val metadata: M?) {

    companion object {

        fun <A : Annotated, M : Any> of(annotated: A, metadata: M?): AnnotatedAndMetadata<A, M> {
            return AnnotatedAndMetadata(annotated, metadata)
        }

    }

}