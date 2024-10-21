package org.cirjson.cirjackson.annotations

/**
 * CirJackson-specific annotation used for indicating that value of annotated property will be "injected", i.e. set
 * based on value configured by `ObjectMapper` (usually on per-call basis). Usually property is not deserialized from
 * CirJSON, although it is possible to have injected value as default and still allow optional override from CirJSON.
 *
 * @property value Logical id of the value to inject; if not specified (or specified as empty String), will use id based
 * on declared type of property.
 *
 * @property useInput Whether matching value from input (if any) is used for annotated property or not; if disabled
 * (`OptionalBoolean.FALSE`), input value (if any) will be ignored; otherwise it will override injected value.
 *
 * Default is `OptionalBoolean.DEFAULT`, which translates to `OptionalBoolean.TRUE`.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJacksonInject(val value: String = "", val useInput: OptionalBoolean = OptionalBoolean.DEFAULT) {

    /**
     * Helper class used to contain information from a single [CirJacksonInject] annotation, as well as to provide
     * possible overrides from non-annotation sources.
     *
     * @property id ID to use to access injected value; if `null`, "default" name, derived from accessor will be used.
     */
    open class Value protected constructor(val id: Any?, val useInput: Boolean?) :
            CirJacksonAnnotationValue<CirJacksonInject> {

        override fun valueFor(): Class<CirJacksonInject> {
            return CirJacksonInject::class.java
        }

        fun withId(id: Any?): Value {
            return if (id == this.id) {
                this
            } else {
                Value(id, useInput)
            }
        }

        fun withUseInput(useInput: Boolean?): Value {
            return if (useInput == this.useInput) {
                this
            } else {
                Value(id, useInput)
            }
        }

        val isIdNotNull
            get() = id != null

        fun willUseInput(default: Boolean): Boolean {
            return useInput ?: default
        }

        override fun toString(): String {
            return "CirJacksonInject.Value(id=$id,useInput=$useInput)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Value

            if (id != other.id) return false
            if (useInput != other.useInput) return false

            return true
        }

        override fun hashCode(): Int {
            var result = 1
            result += id?.hashCode() ?: 0
            result += useInput?.hashCode() ?: 0
            return result
        }

        companion object {

            private val EMPTY = Value(null, null)

            fun construct(id: Any?, useInput: Boolean?): Value {
                val realId = id.takeIf { "" != it }

                return if (empty(realId, useInput)) {
                    EMPTY
                } else {
                    Value(realId, useInput)
                }
            }

            fun from(source: CirJacksonInject?): Value {
                source ?: return EMPTY
                return construct(source.value, source.useInput.asBoolean())
            }

            private fun empty(id: Any?, useInput: Boolean?): Boolean {
                return id == null && useInput == null
            }

        }

    }

}