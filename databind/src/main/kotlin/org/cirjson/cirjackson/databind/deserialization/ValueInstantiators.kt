package org.cirjson.cirjackson.databind.deserialization

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationConfig

/**
 * Interface for providers of [ValueInstantiator] instances. Implemented when an object wants to provide custom value
 * instantiators, usually to support custom value types with alternate constructors, or which need specified
 * post-processing after construction but before binding data.
 */
interface ValueInstantiators {

    /**
     * Method called to find the [ValueInstantiator] to use for creating instances of the specified type during
     * deserialization.
     *
     * Note that no default instantiator is passed at this point: there is a separate method, [modifyValueInstantiator]
     * if you want to use or modify that.
     *
     * @param config Deserialization configuration in use
     *
     * @param beanDescription Additional information about POJO type to be instantiated
     *
     * @return Instantiator to use if custom one wanted, or `null` to indicate "use default instantiator".
     */
    fun findValueInstantiator(config: DeserializationConfig, beanDescription: BeanDescription): ValueInstantiator?

    /**
     * Method called to find the [ValueInstantiator] to use for creating instances of the specified type during
     * deserialization. Note that a default value instantiator is always created first and passed; if an implementation
     * does not want to modify or replace it, it has to return passed instance as is (returning `null` is an error)
     *
     * @param config Deserialization configuration in use
     *
     * @param beanDescription Additional information about POJO type to be instantiated
     *
     * @param defaultValueInstantiator Instantiator that will be used if no changes are made; passed to allow custom
     * instances to use annotation-provided information (note, however, that earlier [ValueInstantiators] may have
     * changed it to a custom instantiator already)
     *
     * @return Instantiator to use; either `defaultValueInstantiator` that was passed, or a custom variant; cannot be `null`.
     */
    fun modifyValueInstantiator(config: DeserializationConfig, beanDescription: BeanDescription,
            defaultValueInstantiator: ValueInstantiator): ValueInstantiator {
        return defaultValueInstantiator
    }

}