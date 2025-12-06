package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.Nulls
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember

/**
 * Simple container class used for storing "additional" metadata about properties. Carved out to reduce number of
 * distinct properties that actual property implementations and placeholders need to store; since instances are
 * immutable, they can be freely shared.
 *
 * @property myRequired Three states: required, not required and unknown; unknown represented as `null`.
 *
 * @property myDescription Optional human-readable description associated with the property.
 *
 * @property myIndex Optional index of the property within containing Object.
 *
 * @property myMergeInfo Settings regarding merging, if property is determined to possibly be mergeable (possibly since
 * global settings may be omitted for non-mergeable types).
 *
 * NOTE: transient since it is assumed that this information is only relevant during initial setup and not needed after
 * full initialization. May be changed if this proves necessary.
 *
 * @property myValueNulls Setting regarding handling of incoming `null`s for value itself.
 *
 * @property myContentNulls Setting regarding handling of incoming `null`s for structured types, content values
 * (array/Collection elements, Map values).
 */
open class PropertyMetadata protected constructor(protected val myRequired: Boolean?,
        protected val myDescription: String?, protected val myIndex: Int?, defaultValue: String?,
        @Transient protected val myMergeInfo: MergeInfo?, protected var myValueNulls: Nulls?,
        protected var myContentNulls: Nulls?) {

    /**
     * Optional default value, as String, for property; not used for any functionality by core databind, offered as
     * metadata for extensions.
     */
    protected val myDefaultValue = defaultValue?.takeUnless { it.isEmpty() }

    /*
     *******************************************************************************************************************
     * Construction, configuration
     *******************************************************************************************************************
     */

    open fun withDescription(description: String?): PropertyMetadata {
        return PropertyMetadata(myRequired, description, myIndex, myDefaultValue, myMergeInfo, myValueNulls,
                myContentNulls)
    }

    open fun withMergeInfo(mergeInfo: MergeInfo?): PropertyMetadata {
        return PropertyMetadata(myRequired, myDescription, myIndex, myDefaultValue, mergeInfo, myValueNulls,
                myContentNulls)
    }

    open fun withValueNulls(valueNulls: Nulls?, contentNulls: Nulls?): PropertyMetadata {
        return PropertyMetadata(myRequired, myDescription, myIndex, myDefaultValue, myMergeInfo, valueNulls,
                contentNulls)
    }

    open fun withDefaultValue(defaultValue: String?): PropertyMetadata {
        val realDefaultValue = defaultValue.takeUnless { it.isNullOrEmpty() }

        if (realDefaultValue == myDefaultValue) {
            return this
        }

        return PropertyMetadata(myRequired, myDescription, myIndex, realDefaultValue, myMergeInfo, myValueNulls,
                myContentNulls)
    }

    open fun withIndex(index: Int?): PropertyMetadata {
        return PropertyMetadata(myRequired, myDescription, index, myDefaultValue, myMergeInfo, myValueNulls,
                myContentNulls)
    }

    open fun withRequired(required: Boolean?): PropertyMetadata {
        if (required == myRequired) {
            return this
        }

        return PropertyMetadata(required, myDescription, myIndex, myDefaultValue, myMergeInfo, myValueNulls,
                myContentNulls)
    }

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open val description: String?
        get() = myDescription

    open val defaultValue: String?
        get() = myDefaultValue

    /**
     * Method for determining whether property has declared "default value", which may be used by extension modules.
     */
    open fun hasDefaultValue(): Boolean {
        return myDefaultValue != null
    }

    open val isRequired: Boolean
        get() = myRequired ?: false

    open val required: Boolean?
        get() = myRequired

    open val index: Int?
        get() = myIndex

    open fun hasIndex(): Boolean {
        return myIndex != null
    }

    open val mergeInfo: MergeInfo?
        get() = myMergeInfo

    open val valueNulls: Nulls?
        get() = myValueNulls

    open val contentNulls: Nulls?
        get() = myContentNulls

    /**
     * Helper class used for containing information about expected merge information for this property, if merging is
     * expected.
     *
     * @property fromDefaults Flag that is set if the information came from global defaults, and not from explicit
     * per-property annotations or per-type config overrides.
     */
    class MergeInfo private constructor(val getter: AnnotatedMember, val fromDefaults: Boolean) {

        companion object {

            fun createForDefaults(getter: AnnotatedMember): MergeInfo {
                return MergeInfo(getter, true)
            }

            fun createForTypeOverride(getter: AnnotatedMember): MergeInfo {
                return MergeInfo(getter, false)
            }

            fun createForPropertyOverride(getter: AnnotatedMember): MergeInfo {
                return MergeInfo(getter, false)
            }

        }

    }

    companion object {

        val STANDARD_REQUIRED = PropertyMetadata(true, null, null, null, null, null, null)

        val STANDARD_OPTIONAL = PropertyMetadata(false, null, null, null, null, null, null)

        val STANDARD_REQUIRED_OR_OPTIONAL = PropertyMetadata(null, null, null, null, null, null, null)

        fun construct(required: Boolean?, description: String?, index: Int?, defaultValue: String?): PropertyMetadata {
            if (description != null || index != null || defaultValue != null) {
                return PropertyMetadata(required, description, index, defaultValue, null, null, null)
            }

            required ?: return STANDARD_REQUIRED_OR_OPTIONAL

            return if (required) STANDARD_REQUIRED else STANDARD_OPTIONAL
        }

    }

}