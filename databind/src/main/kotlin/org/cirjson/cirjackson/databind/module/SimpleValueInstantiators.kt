package org.cirjson.cirjackson.databind.module

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.DeserializationConfig
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiator
import org.cirjson.cirjackson.databind.deserialization.ValueInstantiators
import org.cirjson.cirjackson.databind.type.ClassKey
import kotlin.reflect.KClass

open class SimpleValueInstantiators : ValueInstantiators.Base() {

    /**
     * Mappings from raw (type-erased, i.e. non-generic) types to matching [ValueInstantiator] instances.
     */
    protected val myClassMappings = HashMap<ClassKey, ValueInstantiator>()

    open fun addValueInstantiator(forType: KClass<*>, instantiator: ValueInstantiator): SimpleValueInstantiators {
        myClassMappings[ClassKey(forType)] = instantiator
        return this
    }

    /*
     *******************************************************************************************************************
     * ValueInstantiators implementation
     *******************************************************************************************************************
     */

    override fun findValueInstantiator(config: DeserializationConfig,
            beanDescription: BeanDescription): ValueInstantiator? {
        return myClassMappings[ClassKey(beanDescription.beanClass)]
    }

}