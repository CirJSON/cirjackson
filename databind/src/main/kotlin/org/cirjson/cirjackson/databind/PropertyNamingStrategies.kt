package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedField
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.introspection.AnnotatedParameter

/**
 * Container for standard [PropertyNamingStrategy] implementations and singleton instances.
 */
object PropertyNamingStrategies {

    /*
     *******************************************************************************************************************
     * Static instances that may be referenced
     *******************************************************************************************************************
     */

    /**
     * Naming convention used in Kotlin, where words other than first are capitalized and no separator is used between
     * words. Since this is the native naming convention, naming strategy will not do any transformation between names
     * in data (CirJSON) and POJOs.
     * 
     * Example external property names would be `"numberValue"`, `"namingStrategy"`, `"theDefiniteProof"`.
     */
    val LOWER_CAMEL_CASE = LowerCamelCaseStrategy()

    /**
     * Naming convention used in languages like Pascal, where all words are capitalized and no separator is used between
     * words. See [UpperCamelCaseStrategy] for details.
     * 
     * Example external property names would be `"NumberValue"`, `"NamingStrategy"`, `"TheDefiniteProof"`.
     */
    val UPPER_CAMEL_CASE = UpperCamelCaseStrategy()

    /**
     * Naming convention used in languages like C, where words are in lower-case letters, separated by underscores.
     * See [SnakeCaseStrategy] for details.
     * 
     * Example external property names would be `"number_value"`, `"naming_strategy"`, `"the_definite_proof"`.
     */
    val SNAKE_CASE = SnakeCaseStrategy()

    /**
     * Naming convention in which the words are in upper-case letters, separated by underscores. See
     * [UpperSnakeCaseStrategy] for details.
     */
    val UPPER_SNAKE_CASE = UpperSnakeCaseStrategy()

    /**
     * Naming convention in which all words of the logical name are in lower case, and no separator is used between
     * words. See [LowerCaseStrategy] for details.
     * 
     * Example external property names would be `"numbervalue"`, `"namingstrategy"`, `"thedefiniteproof"`.
     */
    val LOWER_CASE = LowerCaseStrategy()

    /**
     * Naming convention used in languages like Lisp, where words are in lower-case letters, separated by hyphens. See
     * [KebabCaseStrategy] for details.
     * 
     * Example external property names would be `"number-value"`, `"naming-strategy"`, `"the-definite-proof"`.
     */
    val KEBAB_CASE = KebabCaseStrategy()

    /**
     * Naming convention widely used as configuration properties name, where words are in lower-case letters, separated
     * by dots. See [LowerDotCaseStrategy] for details.
     * 
     * Example external property names would be `"number.value"`, `"naming.strategy"`, `"the.definite.proof"`.
     */
    val LOWER_DOT_CASE = LowerDotCaseStrategy()

    /*
     *******************************************************************************************************************
     * Public base class for simple implementations
     *******************************************************************************************************************
     */

    /**
     * Intermediate base class for simple implementations
     */
    abstract class NamingBase : PropertyNamingStrategy() {

        override fun nameForField(config: MapperConfig<*>, field: AnnotatedField, defaultName: String): String {
            return translate(defaultName)
        }

        override fun nameForGetterMethod(config: MapperConfig<*>, method: AnnotatedMethod,
                defaultName: String): String {
            return translate(defaultName)
        }

        override fun nameForSetterMethod(config: MapperConfig<*>, method: AnnotatedMethod,
                defaultName: String): String {
            return translate(defaultName)
        }

        override fun nameForConstructorParameter(config: MapperConfig<*>, constructorParameter: AnnotatedParameter,
                defaultName: String): String {
            return translate(defaultName)
        }

        protected abstract fun translate(propertyName: String): String

        /**
         * Helper method to share implementation between snake and dotted case.
         */
        protected open fun translateLowerCaseWithSeparator(input: String, separator: Char): String {
            val length = input.length

            if (length == 0) {
                return input
            }

            val result = StringBuilder(length + (length shr 1))

            var upperCount = 0

            for (i in 0..<length) {
                val ch = input[i]
                val lc = ch.lowercaseChar()

                if (lc == ch) {
                    if (upperCount > 1) {
                        result.insert(result.length - 1, separator)
                    }

                    upperCount = 0
                } else {
                    if (upperCount == 0 && i > 0) {
                        result.append(separator)
                    }

                    upperCount++
                }

                result.append(lc)
            }

            return result.toString()
        }

    }

    /*
     *******************************************************************************************************************
     * Standard implementations
     *******************************************************************************************************************
     */

    open class SnakeCaseStrategy : NamingBase() {

        /**
         * A [PropertyNamingStrategy] that translates typical camel case property names to lower case CirJSON element
         * names, separated by underscores. This implementation is somewhat lenient, in that it provides some additional
         * translations beyond strictly translating from camel case only. In particular, the following translations are
         * applied by this `PropertyNamingStrategy`.
         *
         * * Every upper case letter in the property name is translated into two characters, an underscore and the lower
         * case equivalent of the target character, with three exceptions.
         * 
         *   1. For contiguous sequences of upper case letters, characters after the first character are replaced only
         *   by their lower case equivalent, and are not preceded by an underscore.
         *   
         *     * This provides for reasonable translations of upper case acronyms, e.g., `"theWWW"` is translated to
         *     `"the_www"`.
         *   
         *   2. An upper case character in the first position of the property name is not preceded by an underscore
         *   character, and is translated only to its lower case equivalent.
         *   
         *     * For example, "Results" is translated to `"results"`, and not to `"_results"`.
         *   
         *   3. An upper case character in the property name that is already preceded by an underscore character is
         *   translated only to its lower case equivalent, and is not preceded by an additional underscore.
         *   
         *     * For example, `"user_Name"` is translated to `"user_name"`, and not to `"user__name"` (with two
         *     underscore characters).
         * 
         * * If the property name starts with an underscore, then that underscore is not included in the translated
         * name, unless the property name is just one character in length, i.e., it is the underscore character. This
         * applies only to the first character of the property name.
         * 
         * These rules result in the following additional example translations from property names to CirJSON element
         * names.
         * 
         * * `"userName"` is translated to `"user_name"`
         * 
         * * `"UserName"` is translated to `"user_name"`
         * 
         * * `"USER_NAME"` is translated to `"user_name"`
         * 
         * * `"user_name"` is translated to `"user_name"` (unchanged)
         * 
         * * `"user"` is translated to `"user"` (unchanged)
         * 
         * * `"User"` is translated to `"user"`
         * 
         * * `"USER"` is translated to `"user"`
         * 
         * * `"_user"` is translated to `"user"`
         * 
         * * `"_User"` is translated to `"user"`
         * 
         * * `"__user"` is translated to `"_user"` (the first of two underscores was removed)
         * 
         * * `"user__name"` is translated to `"user__name"` (unchanged, with two underscores)
         */
        override fun translate(propertyName: String): String {
            val length = propertyName.length

            if (length == 0) {
                return propertyName
            }

            val result = StringBuilder(length * 2)
            var resultLength = 0
            var wasPreviouslyTranslated = false

            for (i in 0..<length) {
                var c = propertyName[i]

                if (i == 0 && c == '_') {
                    continue
                }

                if (c.isUpperCase()) {
                    if (!wasPreviouslyTranslated && resultLength > 0 && result[resultLength - 1] != '_') {
                        result.append('_')
                        resultLength++
                    }

                    c = c.lowercaseChar()
                    wasPreviouslyTranslated = true
                } else {
                    wasPreviouslyTranslated = false
                }

                result.append(c)
                resultLength++
            }

            return if (result.length > length) result.toString() else propertyName
        }

    }

    /**
     * A [PropertyNamingStrategy] that translates an input to the equivalent upper case snake case. The class extends
     * [SnakeCaseStrategy] to retain the snake case conversion functionality offered by the strategy.
     */
    open class UpperSnakeCaseStrategy : SnakeCaseStrategy() {

        override fun translate(propertyName: String): String {
            return super.translate(propertyName).uppercase()
        }

    }

    /**
     * "No-operation" strategy that is equivalent to not specifying any strategy: will simply return suggested standard
     * bean naming as-is.
     */
    open class LowerCamelCaseStrategy : NamingBase() {

        override fun translate(propertyName: String): String {
            return propertyName
        }

    }

    /**
     * A [PropertyNamingStrategy] that translates typical camelCase property names to PascalCase CirJSON element names
     * (i.e., with a capital first letter).  In particular, the following translations are applied by this
     * `PropertyNamingStrategy`.
     *
     * * The first lower-case letter in the property name is translated into its equivalent upper-case representation.
     *
     * This rules result in the following example translation from property names to CirJSON element names.
     * 
     * * `"userName"` is translated to `"UserName"`
     */
    open class UpperCamelCaseStrategy : NamingBase() {

        /**
         * Converts camelCase to PascalCase
         * 
         * For example, `"userName"` would be converted to `"UserName"`.
         *
         * @param propertyName formatted as camelCase string
         * 
         * @return propertyName converted to PascalCase format
         */
        override fun translate(propertyName: String): String {
            if (propertyName.isEmpty()) {
                return propertyName
            }

            val c = propertyName[0]
            val uc = c.lowercaseChar()

            if (c == uc) {
                return propertyName
            }

            val result = StringBuilder(propertyName)
            result[0] = uc
            return result.toString()
        }

    }

    /**
     * Simple strategy where external name simply only uses lower-case characters, and no separators. Conversion from
     * internal name like `"userName"` would be into external name `"username"`.
     */
    open class LowerCaseStrategy : NamingBase() {

        override fun translate(propertyName: String): String {
            return propertyName.lowercase()
        }

    }

    /**
     * Naming strategy similar to [SnakeCaseStrategy], but instead of underscores as separators, uses hyphens. Naming
     * convention traditionally used for languages like Lisp.
     */
    open class KebabCaseStrategy : NamingBase() {

        override fun translate(propertyName: String): String {
            return translateLowerCaseWithSeparator(propertyName, '-')
        }

    }

    /**
     * Naming strategy similar to [KebabCaseStrategy], but instead of hyphens as separators, uses dots. Naming
     * convention widely used as configuration properties name.
     */
    open class LowerDotCaseStrategy : NamingBase() {

        override fun translate(propertyName: String): String {
            return translateLowerCaseWithSeparator(propertyName, '.')
        }

    }

}