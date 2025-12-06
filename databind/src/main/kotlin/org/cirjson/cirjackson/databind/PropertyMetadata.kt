package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.Nulls
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember

open class PropertyMetadata protected constructor(protected val myRequired: Boolean?,
        protected val myDescription: String?, protected val myIndex: Int?, defaultValue: String?,
        protected val myMergeInfo: MergeInfo?, protected val myValueNulls: Nulls?,
        protected val myContentNulls: Nulls?) {

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open val isRequired: Boolean
        get() = TODO("Not yet implemented")

    class MergeInfo private constructor(val getter: AnnotatedMember, val fromDefaults: Boolean) {
    }

    companion object {

        val STANDARD_REQUIRED = PropertyMetadata(true, null, null, null, null, null, null)

        val STANDARD_OPTIONAL = PropertyMetadata(false, null, null, null, null, null, null)

        val STANDARD_REQUIRED_OR_OPTIONAL = PropertyMetadata(null, null, null, null, null, null, null)

    }

}