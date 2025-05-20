package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsonFormatVisitors.CirJsonObjectFormatVisitor

/**
 * Interface that defines API for filter objects use (as configured using
 * [org.cirjson.cirjackson.annotations.CirJsonFilter]) for filtering bean properties to serialize.
 *
 * Note that since this is an interface, it is strongly recommended that custom implementations extend
 * [org.cirjson.cirjackson.databind.serialization.standard.SimpleBeanPropertyFilter], to avoid backwards compatibility
 * issues in case the interface needs to change.
 */
interface PropertyFilter : Snapshottable<PropertyFilter> {

    /**
     * Method called by [BeanSerializer] to let the filter decide what to do with given bean property value: the usual
     * choices are to either filter out (i.e., do nothing) or write using given [PropertyWriter], although filters can
     * choose other to do something different altogether.
     *
     * Typical implementation is something like:
     * ```
     * if (include(writer)) {
     *     writer.serializeAsProperty(pojo, generator, context)
     * }
     * ```
     *
     * @param pojo Object that contains property value to serialize
     *
     * @param generator Generator use for serializing value
     *
     * @param context Provider that can be used for accessing dynamic aspects of serialization processing
     *
     * @param writer Object called to do actual serialization of the field, if not filtered out
     */
    @Throws(Exception::class)
    fun serializeAsProperty(pojo: Any, generator: CirJsonGenerator, context: SerializerProvider, writer: PropertyWriter)

    /**
     * Method called by container to let the filter decide what to do with given element value: the usual choices are to
     * either filter out (i.e., do nothing) or write using given [PropertyWriter], although filters can choose other to
     * do something different altogether.
     *
     * Typical implementation is something like:
     * ```
     * if (include(writer)) {
     *     writer.serializeAsElement(value, generator, context)
     * }
     * ```
     *
     * @param value Element value being serialized
     *
     * @param generator Generator use for serializing value
     *
     * @param context Provider that can be used for accessing dynamic aspects of serialization processing
     *
     * @param writer Object called to do actual serialization of the field, if not filtered out
     */
    @Throws(Exception::class)
    fun serializeAsElement(value: Any, generator: CirJsonGenerator, context: SerializerProvider, writer: PropertyWriter)

    /**
     * Method called by [BeanSerializer] to let the filter determine whether, and in what form the given property exists
     * within the parent, or root, schema. Filters can omit adding the property to the node, or choose the form of the
     * schema value for the property
     *
     * Typical implementation is something like:
     * ```
     * if (include(writer)) {
     *     writer.depositSchemaProperty(visitor, context)
     * }
     * ```
     *
     * @param writer Bean property serializer to use to create schema value
     *
     * @param visitor CirJsonObjectFormatVisitor which should be aware of the property's existence
     *
     * @param context Serialization context
     */
    fun depositSchemaProperty(writer: PropertyWriter, visitor: CirJsonObjectFormatVisitor, context: SerializerProvider)

}