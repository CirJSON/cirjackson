package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.DeserializationContext
import org.cirjson.cirjackson.databind.util.AccessPattern

/**
 * Helper interface implemented by classes that are to be used as `null` providers during deserialization. Most
 * importantly implemented by [org.cirjson.cirjackson.databind.ValueDeserializer] (as a mix-in interface), but also by
 * converters used to support more configurable `null` replacement.
 */
interface NullValueProvider {

    /**
     * Method called to possibly convert incoming `null` token (read via the underlying streaming input source) into
     * other value of type accessor supports. May return `null`, or value compatible with type binding.
     *
     * NOTE: if [nullAccessPattern] returns `ALWAYS_NULL` or `CONSTANT`, this method WILL NOT use provided `context` and
     * it may thus be passed as `null`.
     */
    fun getNullValue(context: DeserializationContext?): Any?

    /**
     * Accessor that may be used to determine if and when provider must be called to access `null` replacement value.
     */
    val nullAccessPattern: AccessPattern

    /**
     * Method called to determine placeholder value to be used for cases where no value was obtained from input, but we
     * must pass a value nonetheless: the common case is that of Creator methods requiring passing a value for every
     * parameter. Usually this is same as [getNullValue] (which in turn is usually simply `null`), but it can be
     * overridden for specific types: most notable scalar types must use "default" values.
     *
     * This method needs to be called every time a determination is made.
     *
     * Default implementation simply calls and returns [getNullValue].
     */
    fun getAbsentValue(context: DeserializationContext?): Any? {
        return getNullValue(context)
    }

}