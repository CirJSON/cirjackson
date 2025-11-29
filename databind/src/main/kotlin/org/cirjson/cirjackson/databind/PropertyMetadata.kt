package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.Nulls
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember

open class PropertyMetadata protected constructor(val required: Boolean?, val description: String?, val index: Int?,
        defaultValue: String?, val mergeInfo: MergeInfo?, val valueNulls: Nulls?, val contentNulls: Nulls?) {

    val isRequired: Boolean
        get() = TODO("Not yet implemented")

    class MergeInfo(val getter: AnnotatedMember, val fromDefaults: Boolean) {
    }

    companion object {

        val STANDARD_REQUIRED = PropertyMetadata(true, null, null, null, null, null, null)

        val STANDARD_OPTIONAL = PropertyMetadata(false, null, null, null, null, null, null)

        val STANDARD_REQUIRED_OR_OPTIONAL = PropertyMetadata(null, null, null, null, null, null, null)

    }

}