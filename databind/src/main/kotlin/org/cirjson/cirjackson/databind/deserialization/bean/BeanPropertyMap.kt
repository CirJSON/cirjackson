package org.cirjson.cirjackson.databind.deserialization.bean

import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.deserialization.SettableBeanProperty
import java.util.*

class BeanPropertyMap : Iterable<SettableBeanProperty> {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    val isCaseInsensitive: Boolean

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    protected constructor(properties: Collection<SettableBeanProperty>, aliasDefinitions: Array<Array<PropertyName>?>?,
            locale: Locale, caseInsensitive: Boolean, assignIndexes: Boolean) {
        isCaseInsensitive = caseInsensitive
    }

    /*
     *******************************************************************************************************************
     * Public API, simple accessors
     *******************************************************************************************************************
     */

    override fun iterator(): Iterator<SettableBeanProperty> {
        TODO("Not yet implemented")
    }

}