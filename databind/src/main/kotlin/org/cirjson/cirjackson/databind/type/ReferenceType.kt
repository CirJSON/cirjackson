package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.type.ReferenceType.Companion.upgradeFrom
import kotlin.reflect.KClass

/**
 * Specialized [SimpleType] for types that are referential types, that is, values that can be dereferenced to another
 * value (or `null`), of different type. Referenced type is accessible using [contentType].
 */
@Suppress("EqualsOrHashCode")
open class ReferenceType : SimpleType {

    protected val myReferencedType: KotlinType

    protected val myAnchorType: KotlinType

    /**
     * Essential type used for type ids, for example if type id is needed for referencing type with polymorphic
     * handling. Typically, initialized when a [SimpleType] is upgraded into reference type, but NOT changed if being
     * sub-classed.
     */
    protected constructor(clazz: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?, referencedType: KotlinType, anchorType: KotlinType?,
            valueHandler: Any?, typeHandler: Any?, isUsedAsStaticType: Boolean) : super(clazz, bindings, superClass,
            superInterfaces, referencedType.hashCode(), valueHandler, typeHandler, isUsedAsStaticType) {
        myReferencedType = referencedType
        myAnchorType = anchorType ?: this
    }

    /**
     * Constructor used when upgrading into this type (via [upgradeFrom], the usual way for
     * [ReferenceTypes][ReferenceType] to come into existence. Sets up what is considered the "base" reference type.
     */
    protected constructor(base: TypeBase, type: KotlinType) : super(base) {
        myReferencedType = type
        myAnchorType = this
    }

    override fun withContentType(contentType: KotlinType): KotlinType {
        if (myReferencedType === contentType) {
            return this
        }

        return ReferenceType(myClass, myBindings, mySuperClass, mySuperInterfaces, contentType, myAnchorType,
                myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withTypeHandler(handler: Any?): ReferenceType {
        if (myTypeHandler === handler) {
            return this
        }

        return ReferenceType(myClass, myBindings, mySuperClass, mySuperInterfaces, myReferencedType, myAnchorType,
                myValueHandler, handler, isUsedAsStaticType)
    }

    override fun withContentTypeHandler(handler: Any?): ReferenceType {
        if (myReferencedType.typeHandler === handler) {
            return this
        }

        return ReferenceType(myClass, myBindings, mySuperClass, mySuperInterfaces,
                myReferencedType.withTypeHandler(handler), myAnchorType, myValueHandler, myTypeHandler,
                isUsedAsStaticType)
    }

    override fun withValueHandler(handler: Any?): ReferenceType {
        if (myValueHandler === handler) {
            return this
        }

        return ReferenceType(myClass, myBindings, mySuperClass, mySuperInterfaces, myReferencedType, myAnchorType,
                handler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withContentValueHandler(handler: Any?): ReferenceType {
        if (myReferencedType.valueHandler === handler) {
            return this
        }

        return ReferenceType(myClass, myBindings, mySuperClass, mySuperInterfaces,
                myReferencedType.withValueHandler(handler), myAnchorType, myValueHandler, myTypeHandler,
                isUsedAsStaticType)
    }

    override fun withStaticTyping(): ReferenceType {
        if (isUsedAsStaticType) {
            return this
        }

        return ReferenceType(myClass, myBindings, mySuperClass, mySuperInterfaces, myReferencedType.withStaticTyping(),
                myAnchorType, myValueHandler, myTypeHandler, true)
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): ReferenceType {
        return ReferenceType(raw, myBindings, superClass, superInterfaces, myReferencedType, myAnchorType,
                myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun buildCanonicalName(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(myClass.qualifiedName)

        if (hasNTypeParameters(1)) {
            stringBuilder.append('<')
            stringBuilder.append(myReferencedType.toCanonical())
            stringBuilder.append('>')
        }

        return stringBuilder.toString()
    }

    /*
     *******************************************************************************************************************
     * Public API overrides
     *******************************************************************************************************************
     */

    override val contentType: KotlinType
        get() = myReferencedType

    override val referencedType: KotlinType
        get() = myReferencedType

    override fun hasContentType(): Boolean {
        return true
    }

    override val isReferenceType: Boolean
        get() = true

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
        classSignature(myClass, stringBuilder, false)
        stringBuilder.append('<')
        myReferencedType.getErasedSignature(stringBuilder)
        stringBuilder.append(">;")
        return stringBuilder
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    open val anchorType: KotlinType
        get() = myAnchorType

    /**
     * Convenience accessor that allows checking whether `this` is the anchor type itself; if not, it must be one of
     * supertypes that is also a [ReferenceType]
     */
    open val isAnchorType: Boolean
        get() = myAnchorType === this

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "[reference type, class ${buildCanonicalName()}<$myReferencedType>]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is ReferenceType || this::class != other::class) {
            return false
        }

        return myClass == other.myClass && myBindings == other.myBindings
    }

    companion object {

        /**
         * Factory method that can be used to "upgrade" a basic type into collection-like one; usually done via
         * [TypeModifier]
         *
         * @param baseType Resolved non-reference type (usually [SimpleType]) that is being upgraded
         *
         * @param referencedType Referenced type; usually the first and only type parameter, but not necessarily
         */
        fun upgradeFrom(baseType: KotlinType, referencedType: KotlinType): ReferenceType {
            if (baseType !is TypeBase) {
                throw IllegalArgumentException("Cannot upgrade from an instance of ${baseType::class}")
            }

            return ReferenceType(baseType, referencedType)
        }

        fun construct(clazz: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
                superInterfaces: Array<KotlinType>?, referencedType: KotlinType): ReferenceType {
            return ReferenceType(clazz, bindings, superClass, superInterfaces, referencedType, null, null, null, false)
        }

    }

}