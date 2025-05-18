package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider

/**
 * Interface for visitor callbacks, when type in question can be any of legal CirJSON types.
 *
 * In most cases, it will make more sense to extend [CirJsonFormatVisitorWrapper.Base] instead of directly implementing
 * this interface.
 */
interface CirJsonFormatVisitorWrapper : CirJsonFormatVisitorWithSerializerProvider {

    /**
     * @param type Declared type of visited property (or List element) in Kotlin
     */
    fun expectObjectFormat(type: KotlinType): CirJsonObjectFormatVisitor?

    /**
     * @param type Declared type of visited property (or List element) in Kotlin
     */
    fun expectArrayFormat(type: KotlinType): CirJsonArrayFormatVisitor?

    /**
     * @param type Declared type of visited property (or List element) in Kotlin
     */
    fun expectStringFormat(type: KotlinType): CirJsonStringFormatVisitor?

    /**
     * @param type Declared type of visited property (or List element) in Kotlin
     */
    fun expectNumberFormat(type: KotlinType): CirJsonNumberFormatVisitor?

    /**
     * @param type Declared type of visited property (or List element) in Kotlin
     */
    fun expectIntFormat(type: KotlinType): CirJsonIntFormatVisitor?

    /**
     * @param type Declared type of visited property (or List element) in Kotlin
     */
    fun expectBooleanFormat(type: KotlinType): CirJsonBooleanFormatVisitor?

    /**
     * @param type Declared type of visited property (or List element) in Kotlin
     */
    fun expectNullFormat(type: KotlinType): CirJsonNullFormatVisitor?

    /**
     * @param type Declared type of visited property (or List element) in Kotlin
     */
    fun expectAnyFormat(type: KotlinType): CirJsonAnyFormatVisitor?

    /**
     * Method called when type is of Kotlin [Map] type, and will be serialized as a CirJSON Object.
     *
     * @param type Declared type of visited property (or List element) in Kotlin
     */
    fun expectMapFormat(type: KotlinType): CirJsonMapFormatVisitor?

    /**
     * Empty "no-op" implementation of [CirJsonFormatVisitorWrapper], suitable for subclassing. Does implement
     * [provider] as expected; other methods simply return `null` and do nothing.
     */
    open class Base(override var provider: SerializerProvider? = null) : CirJsonFormatVisitorWrapper {

        override fun expectObjectFormat(type: KotlinType): CirJsonObjectFormatVisitor? {
            return null
        }

        override fun expectArrayFormat(type: KotlinType): CirJsonArrayFormatVisitor? {
            return null
        }

        override fun expectStringFormat(type: KotlinType): CirJsonStringFormatVisitor? {
            return null
        }

        override fun expectNumberFormat(type: KotlinType): CirJsonNumberFormatVisitor? {
            return null
        }

        override fun expectIntFormat(type: KotlinType): CirJsonIntFormatVisitor? {
            return null
        }

        override fun expectBooleanFormat(type: KotlinType): CirJsonBooleanFormatVisitor? {
            return null
        }

        override fun expectNullFormat(type: KotlinType): CirJsonNullFormatVisitor? {
            return null
        }

        override fun expectAnyFormat(type: KotlinType): CirJsonAnyFormatVisitor? {
            return null
        }

        override fun expectMapFormat(type: KotlinType): CirJsonMapFormatVisitor? {
            return null
        }

    }

}