package org.cirjson.cirjackson.databind.serialization.bean

import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.util.NameTransformer

open class UnwrappingBeanPropertyWriter : BeanPropertyWriter {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor(base: BeanPropertyWriter, unwrapper: NameTransformer?) : super(base) {
    }

}