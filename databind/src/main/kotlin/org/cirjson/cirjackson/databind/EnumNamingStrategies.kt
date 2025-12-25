package org.cirjson.cirjackson.databind

/**
 * A container class for implementations of the [EnumNamingStrategy] interface.
 */
object EnumNamingStrategies {

    /**
     * An implementation of [EnumNamingStrategy] that converts enum names in the typical upper
     * snake case format to camel case format. This implementation follows three rules
     * described below.
     * 
     * 1. converts any character preceded by an underscore into upper case character,
     * regardless of its original case (upper or lower).
     * 
     * 2. converts any character NOT preceded by an underscore into a lower case character,
     * regardless of its original case (upper or lower).
     * 
     * 3. converts contiguous sequence of underscores into a single underscore.
     * 
     * WARNING: Naming conversion conflicts caused by underscore usage should be handled by client.
     * e.g. Both <code>PEANUT_BUTTER</code>, <code>PEANUT__BUTTER</code> are converted into "peanutButter".
     * And "peanutButter" will be deserialized into enum with smaller <code>Enum.ordinal()</code> value.
     * 
     * These rules result in the following example conversions from upper snakecase names
     * to camelcase names.
     * 
     * * "USER_NAME" is converted into "userName"
     * 
     * * "USER______NAME" is converted into "userName"
     * 
     * * "USERNAME" is converted into "username"
     * 
     * * "User__Name" is converted into "userName"
     * 
     * * "_user_name" is converted into "UserName"
     * 
     * * "_user_name_s" is converted into "UserNameS"
     * 
     * * "__Username" is converted into "Username"
     * 
     * * "__username" is converted into "Username"
     * 
     * * "username" is converted into "username"
     * 
     * * "Username" is converted into "username"
     */
    open class CamelCaseStrategy : EnumNamingStrategy {

        override fun convertEnumToExternalName(enumName: String?): String? {
            enumName ?: return null

            val underscore = "_"
            var out: StringBuilder? = null
            var iterationCount = 0
            var lastSeparatorIndex = -1

            do {
                lastSeparatorIndex = indexIn(enumName, lastSeparatorIndex + 1)

                if (lastSeparatorIndex == -1) {
                    continue
                }

                if (iterationCount == 0) {
                    out = StringBuilder(enumName.length + 4 * underscore.length)
                    out.append(toLowerCase(enumName.substring(iterationCount, lastSeparatorIndex)))
                } else {
                    out!!.append(normalizeWord(enumName.substring(iterationCount, lastSeparatorIndex)))
                }

                iterationCount = lastSeparatorIndex + underscore.length
            } while (lastSeparatorIndex != -1)

            if (iterationCount == 0) {
                return toLowerCase(enumName)
            }

            out!!.append(normalizeWord(enumName.substring(iterationCount)))
            return out.toString()
        }

        companion object {

            /**
             * An instance of [CamelCaseStrategy] for reuse.
             */
            val INSTANCE = CamelCaseStrategy()

            private fun indexIn(sequence: CharSequence, start: Int): Int {
                val length = sequence.length

                for (i in start..<length) {
                    if (sequence[i] == '_') {
                        return i
                    }
                }

                return -1
            }

            private fun normalizeWord(word: String): String {
                if (word.isEmpty()) {
                    return word
                }

                return charToUpperCaseIfLower(word[0]) + toLowerCase(word.substring(1))
            }

            private fun toLowerCase(string: String): String {
                val length = string.length
                val builder = StringBuilder(length)

                for (c in string) {
                    builder.also { charToLowerCaseIfUpper(c) }
                }

                return builder.toString()
            }

            private fun charToUpperCaseIfLower(c: Char): String {
                return c.takeUnless { it.isLowerCase() }?.toString() ?: c.uppercase()
            }

            private fun charToLowerCaseIfUpper(c: Char): String {
                return c.takeUnless { it.isUpperCase() }?.toString() ?: c.lowercase()
            }

        }

    }

}