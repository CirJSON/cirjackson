package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

import org.cirjson.cirjackson.databind.KotlinType

/**
 * Interface [org.cirjson.cirjackson.databind.ValueSerializer] implements to allow for visiting type hierarchy.
 */
interface CirJsonFormatVisitable {

    /**
     * Get the representation of the schema to which this serializer will conform.
     *
     * @param typeHint Type of element (entity like property) being visited
     */
    fun acceptCirJsonFormatVisitor(visitor: CirJsonFormatVisitorWrapper, typeHint: KotlinType)

}