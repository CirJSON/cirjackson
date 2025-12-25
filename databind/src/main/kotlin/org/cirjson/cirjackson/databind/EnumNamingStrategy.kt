package org.cirjson.cirjackson.databind

/**
 * Defines how the string representation of an enum is converted into an external property name for mapping during
 * deserialization.
 */
fun interface EnumNamingStrategy {

    /**
     * Translates the given `enumName` into an external property name according to the implementation of this
     * [EnumNamingStrategy].
     *
     * @param enumName the name of the enum value to translate
     *
     * @return the external property name that corresponds to the given `enumName` according to the implementation of
     * this [EnumNamingStrategy].
     */
    fun convertEnumToExternalName(enumName: String?): String?

}