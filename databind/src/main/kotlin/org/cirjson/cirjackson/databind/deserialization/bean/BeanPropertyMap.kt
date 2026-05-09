package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import java.util.*

open class BeanPropertyMap : Iterable<SettableBeanProperty> {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    private val myCaseInsensitive: Boolean

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    protected constructor(properties: Collection<SettableBeanProperty>, aliasDefinitions: Array<Array<PropertyName>?>?,
            locale: Locale, caseInsensitive: Boolean, assignIndexes: Boolean) {
        myCaseInsensitive = caseInsensitive
    }

    /*
     *******************************************************************************************************************
     * Public API, simple accessors
     *******************************************************************************************************************
     */

    open val isCaseInsensitive: Boolean
        get() = myCaseInsensitive

    override fun iterator(): Iterator<SettableBeanProperty> {
        TODO("Not yet implemented")
    }

}