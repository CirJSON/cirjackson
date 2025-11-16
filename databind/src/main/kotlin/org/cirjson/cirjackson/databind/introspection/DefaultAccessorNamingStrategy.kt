package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.MapperFeature
import org.cirjson.cirjackson.databind.annotation.CirJsonPOJOBuilder
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.record.recordFieldNames
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Default [AccessorNamingStrategy] used by Jackson: to be used either as-is, or as base-class with overrides.
 *
 * @property myMutatorPrefix Prefix used by auto-detected mutators ("setters"): usually "set", but differs for builder
 * objects ("with" by default).
 *
 * @property myBaseNameValidator Optional validator for checking that base name
 */
open class DefaultAccessorNamingStrategy protected constructor(protected val myConfig: MapperConfig<*>,
        protected val myForClass: AnnotatedClass, protected val myMutatorPrefix: String?,
        protected val myGetterPrefix: String?, protected val myISGetterPrefix: String?,
        protected val myBaseNameValidator: BaseNameValidator?) : AccessorNamingStrategy() {

    protected val myIsGettersNonBoolean = myConfig.isEnabled(MapperFeature.ALLOW_IS_GETTERS_FOR_NON_BOOLEAN)

    override fun findNameForIsGetter(method: AnnotatedMethod, name: String): String? {
        myISGetterPrefix ?: return null

        if (!myIsGettersNonBoolean && !booleanType(method.type)) {
            return null
        }

        if (!name.startsWith(myISGetterPrefix)) {
            return null
        }

        return standardManglePropertyName(name, myISGetterPrefix.length)
    }

    private fun booleanType(type: KotlinType): Boolean {
        val realType = type.takeUnless { it.isReferenceType } ?: type.referencedType!!
        return realType.hasRawClass(Boolean::class) || type.hasRawClass(AtomicBoolean::class)
    }

    override fun findNameForRegularGetter(method: AnnotatedMethod, name: String): String? {
        if (myGetterPrefix == null || !name.startsWith(myGetterPrefix)) {
            return null
        }

        if (name == "getMetaClass" && isGroovyMetaClassGetter(method)) {
            return null
        }

        return standardManglePropertyName(name, myGetterPrefix.length)
    }

    override fun findNameForMutator(method: AnnotatedMethod, name: String): String? {
        if (myMutatorPrefix == null || !name.startsWith(myMutatorPrefix)) {
            return null
        }

        return standardManglePropertyName(name, myMutatorPrefix.length)
    }

    override fun modifyFieldName(field: AnnotatedField, name: String): String? {
        return name
    }

    /*
     *******************************************************************************************************************
     * Name-mangling methods
     *******************************************************************************************************************
     */

    protected open fun standardManglePropertyName(baseName: String, offset: Int): String? {
        val end = baseName.length

        if (offset == end) {
            return null
        }

        val c0 = baseName[offset]

        if (myBaseNameValidator != null && !myBaseNameValidator.accept(c0, baseName, offset)) {
            return null
        }

        val c1 = c0.lowercase()[0]

        if (c0 == c1) {
            baseName.substring(offset)
        }

        if (offset + 1 < end && baseName[offset + 1].isUpperCase()) {
            baseName.substring(offset)
        }

        return "$c1${baseName.substring(offset + 1, end)}"
    }

    /*
     *******************************************************************************************************************
     * Other methods
     *******************************************************************************************************************
     */

    protected open fun isGroovyMetaClassGetter(annotatedMethod: AnnotatedMethod): Boolean {
        return annotatedMethod.rawType.qualifiedName!!.startsWith("groovy.lang")
    }

    /*
     *******************************************************************************************************************
     * Helper interface
     *******************************************************************************************************************
     */

    /**
     * Definition of a handler API to use for checking whether given base name (remainder of accessor method name after
     * removing prefix) is acceptable based on various rules.
     */
    fun interface BaseNameValidator {

        fun accept(firstChar: Char, baseName: String, offset: Int): Boolean

    }

    /*
     *******************************************************************************************************************
     * Standard Provider implementation
     *******************************************************************************************************************
     */

    /**
     * Provider for [DefaultAccessorNamingStrategy].
     *
     * Default instance will use following default prefixes:
     *
     * * Setter for regular POJOs: `"set"`
     *
     * * Builder-mutator: `"with"`
     *
     * * Regular getter: `"get"`
     *
     * * Is-getter (for Boolean values): `"is"`
     *
     * and no additional restrictions on base names accepted (configurable for limits using [BaseNameValidator]),
     * allowing names like `"get_value()"` and `"getvalue()"`.
     */
    open class Provider protected constructor(protected val mySetterPrefix: String?,
            protected val myWithPrefix: String?, protected val myGetterPrefix: String?,
            protected val myIsGetterPrefix: String?, protected val myBaseNameValidator: BaseNameValidator?) :
            AccessorNamingStrategy.Provider() {

        constructor() : this("set", CirJsonPOJOBuilder.DEFAULT_WITH_PREFIX, "get", "is", null)

        constructor(provider: Provider, setterPrefix: String?, withPrefix: String?, getterPrefix: String?,
                isGetterPrefix: String?) : this(setterPrefix, withPrefix, getterPrefix, isGetterPrefix,
                provider.myBaseNameValidator)

        constructor(provider: Provider, validator: BaseNameValidator?) : this(provider.mySetterPrefix,
                provider.myWithPrefix, provider.myGetterPrefix, provider.myIsGetterPrefix, validator)

        /**
         * Mutant factory for changing the prefix used for "setter" methods
         *
         * @param prefix Prefix to use; or empty String `""` to not use any prefix (meaning signature-compatible method
         * name is used as the property basename (and subject to name mangling)), or `null` to prevent name-based
         * detection.
         *
         * @return Provider instance with specified setter-prefix
         */
        open fun withSetterPrefix(prefix: String?): Provider {
            return Provider(this, prefix, myWithPrefix, myGetterPrefix, myIsGetterPrefix)
        }

        /**
         * Mutant factory for changing the prefix used for Builders (from default
         * [CirJsonPOJOBuilder.DEFAULT_WITH_PREFIX])
         *
         * @param prefix Prefix to use; or empty String `""` to not use any prefix (meaning signature-compatible method
         * name is used as the property basename (and subject to name mangling)), or `null` to prevent name-based
         * detection.
         *
         * @return Provider instance with specified with-prefix
         */
        open fun withBuilderPrefix(prefix: String?): Provider {
            return Provider(this, mySetterPrefix, prefix, myGetterPrefix, myIsGetterPrefix)
        }

        /**
         * Mutant factory for changing the prefix used for "getter" methods
         *
         * @param prefix Prefix to use; or empty String `""` to not use any prefix (meaning signature-compatible method
         * name is used as the property basename (and subject to name mangling)), or `null` to prevent name-based
         * detection.
         *
         * @return Provider instance with specified getter-prefix
         */
        open fun withGetterPrefix(prefix: String?): Provider {
            return Provider(this, mySetterPrefix, myWithPrefix, prefix, myIsGetterPrefix)
        }

        /**
         * Mutant factory for changing the prefix used for "is-getter" methods (getters that return boolean/Boolean
         * value).
         *
         * @param prefix Prefix to use; or empty String `""` to not use any prefix (meaning signature-compatible method
         * name is used as the property basename (and subject to name mangling)). or `null` to prevent name-based
         * detection.
         *
         * @return Provider instance with specified is-getter-prefix
         */
        open fun withIsGetterPrefix(prefix: String?): Provider {
            return Provider(this, mySetterPrefix, myWithPrefix, myGetterPrefix, prefix)
        }

        /**
         * Mutant factory for changing the rules regarding which characters are allowed as the first character of
         * property base name, after checking and removing prefix.
         *
         * For example, consider "getter" method candidate (no arguments, has return type) named `getValue()` is
         * considered, with "getter-prefix" defined as `get`, then base name is `Value` and the first character to
         * consider is `V`. Upper-case letters are always accepted so this is fine. But with similar settings, method
         * `get_value()` would only be recognized as getter if `allowNonLetterFirstChar` is set to `true`: otherwise it
         * will not be considered a getter-method. Similarly "is-getter" candidate method with name `island()` would
         * only be considered if `allowLowerCaseFirstChar` is set to `true`.
         *
         * @param allowLowerCaseFirstChar Whether base names that start with lower-case letter (like `"a"` or `"b"`) are
         * accepted as valid or not: consider difference between "setter-methods" `setValue()` and `setvalue()`.
         *
         * @param allowNonLetterFirstChar  Whether base names that start with non-letter character (like `"_"` or number
         * `1`) are accepted as valid or not: consider difference between "setter-methods" `setValue()` and
         * `set_value()`.
         *
         * @return Provider instance with specified validity rules
         */
        open fun withFirstCharAcceptance(allowLowerCaseFirstChar: Boolean, allowNonLetterFirstChar: Boolean): Provider {
            return withBaseNameValidator(
                    FirstCharBasedValidator.forFirstNameRule(allowLowerCaseFirstChar, allowNonLetterFirstChar))
        }

        /**
         * Mutant factory for specifying validator that is used to further verify that base name derived from accessor
         * name is acceptable: this can be used to add further restrictions such as limit that the first character of
         * the base name is an upper-case letter.
         *
         * @param validator Validator to use, if any; `null` to indicate no additional rules
         *
         * @return Provider instance with specified base name validator to use, if any
         */
        open fun withBaseNameValidator(validator: BaseNameValidator?): Provider {
            return Provider(this, validator)
        }

        override fun forPOJO(config: MapperConfig<*>, valueClass: AnnotatedClass): AccessorNamingStrategy {
            return DefaultAccessorNamingStrategy(config, valueClass, mySetterPrefix, myGetterPrefix, myIsGetterPrefix,
                    myBaseNameValidator)
        }

        override fun forBuilder(config: MapperConfig<*>, builderClass: AnnotatedClass,
                valueTypeDescription: BeanDescription): AccessorNamingStrategy {
            val annotationIntrospector = config.takeIf { it.isAnnotationProcessingEnabled }?.annotationIntrospector
            val builderConfig = annotationIntrospector?.findPOJOBuilderConfig(config, builderClass)
            val mutatorPrefix = builderConfig?.withPrefix ?: myWithPrefix
            return DefaultAccessorNamingStrategy(config, builderClass, mutatorPrefix, myGetterPrefix, myIsGetterPrefix,
                    myBaseNameValidator)
        }

        override fun forRecord(config: MapperConfig<*>, recordClass: AnnotatedClass): AccessorNamingStrategy {
            return RecordNaming(config, recordClass)
        }

    }

    /**
     * Simple implementation of [BaseNameValidator] that checks the first character and nothing else.
     *
     * Instances are to be constructed using method [FirstCharBasedValidator.forFirstNameRule].
     */
    open class FirstCharBasedValidator(private val myAllowLowerCaseFirstChar: Boolean,
            private val myAllowNonLetterFirstChar: Boolean) : BaseNameValidator {

        override fun accept(firstChar: Char, baseName: String, offset: Int): Boolean {
            if (firstChar.isLetter()) {
                return myAllowLowerCaseFirstChar || !firstChar.isLowerCase()
            }

            return myAllowNonLetterFirstChar
        }

        companion object {

            /**
             * Factory method to use for getting an instance with specified first-character restrictions, if any; or
             * `null` if no checking is needed.
             *
             * @param allowLowerCaseFirstChar Whether base names that start with lower-case letter (like `"a"` or `"b"`)
             * are accepted as valid or not: consider difference between "setter-methods" `setValue()` and `setvalue()`.
             *
             * @param allowNonLetterFirstChar  Whether base names that start with non-letter character (like `"_"` or
             * number `1`) are accepted as valid or not: consider difference between "setter-methods" `setValue()` and
             * `set_value()`.
             *
             * @return Validator instance to use, if any; `null` to indicate no additional rules applied (case when both
             * arguments are `false`)
             */
            fun forFirstNameRule(allowLowerCaseFirstChar: Boolean,
                    allowNonLetterFirstChar: Boolean): BaseNameValidator? {
                if (!allowLowerCaseFirstChar && !allowNonLetterFirstChar) {
                    return null
                }

                return FirstCharBasedValidator(allowLowerCaseFirstChar, allowNonLetterFirstChar)
            }

        }

    }

    /**
     * Implementation used for supporting "non-prefix" naming convention of Java 14 `java.lang.Record` types, and in
     * particular find default accessors for declared record fields.
     *
     * Current / initial implementation will also recognize additional "normal" getters ("get"-prefix) and is-getters
     * ("is"-prefix and boolean return value) by name.
     */
    open class RecordNaming(config: MapperConfig<*>, forClass: AnnotatedClass) :
            DefaultAccessorNamingStrategy(config, forClass, null, "get", "is", null) {

        protected val myFieldNames = forClass.rawType.recordFieldNames?.toSet() ?: emptySet()

        override fun findNameForRegularGetter(method: AnnotatedMethod, name: String): String? {
            if (name in myFieldNames) {
                return name
            }

            return super.findNameForRegularGetter(method, name)
        }

    }

}