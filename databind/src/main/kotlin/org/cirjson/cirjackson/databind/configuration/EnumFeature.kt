package org.cirjson.cirjackson.databind.configuration

enum class EnumFeature(override val isEnabledByDefault: Boolean) : DatatypeFeature {

    /**
     * Feature that determines standard deserialization mechanism used for Enum values: if enabled, Enums are assumed to
     * have been serialized using index of `Enum`;
     *
     * Note: this feature should be symmetric to
     * [org.cirjson.cirjackson.databind.SerializationFeature.WRITE_ENUM_KEYS_USING_INDEX].
     *
     * The feature is disabled by default.
     */
    READ_ENUM_KEYS_USING_INDEX(false),

    /**
     * Feature that determines standard serialization mechanism used for Enum values: if enabled, return value of
     * `Enum.name.lowercase()` is used; if disabled, return value of `Enum.name` is used.
     *
     * NOTE: this feature CANNOT be changed on a per-call basis: it will have to be set on `ObjectMapper` before use.
     *
     * The feature is disabled by default.
     */
    WRITE_ENUMS_TO_LOWERCASE(false);

    override val mask = 1 shl ordinal

    override fun isEnabledIn(flags: Int): Boolean {
        return flags and mask != 0
    }

    override fun featureIndex(): Int {
        return FEATURE_INDEX
    }

    companion object {

        const val FEATURE_INDEX = DatatypeFeatures.FEATURE_INDEX_ENUM

    }

}