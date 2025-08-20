package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.type.IterationType.Companion.upgradeFrom
import kotlin.reflect.KClass

/**
 * Specialized [SimpleType] for types that allow iteration over Collection(-like) types: this includes types like
 * [Iterator]. Iterated (content) type is accessible using [contentType].
 */
open class IterationType : SimpleType {

    protected val myIteratedType: KotlinType

    protected constructor(clazz: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?, iteratedType: KotlinType, valueHandler: Any?, typeHandler: Any?,
            isUsedAsStaticType: Boolean) : super(clazz, bindings, superClass, superInterfaces, iteratedType.hashCode(),
            valueHandler, typeHandler, isUsedAsStaticType) {
        myIteratedType = iteratedType
    }

    /**
     * Constructor used when upgrading into this type (via [upgradeFrom], the usual way for
     * [IterationTypes][IterationType] to come into existence. Sets up what is considered the "base" iteration type.
     */
    protected constructor(base: TypeBase, iteratedType: KotlinType) : super(base) {
        myIteratedType = iteratedType
    }

    override fun withContentType(contentType: KotlinType): IterationType {
        if (myIteratedType === contentType) {
            return this
        }

        return IterationType(myClass, myBindings, mySuperClass, mySuperInterfaces, contentType, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withTypeHandler(handler: Any?): IterationType {
        if (myTypeHandler === handler) {
            return this
        }

        return IterationType(myClass, myBindings, mySuperClass, mySuperInterfaces, myIteratedType, myValueHandler,
                handler, isUsedAsStaticType)
    }

    override fun withContentTypeHandler(handler: Any?): IterationType {
        if (myIteratedType.typeHandler === handler) {
            return this
        }

        return IterationType(myClass, myBindings, mySuperClass, mySuperInterfaces,
                myIteratedType.withTypeHandler(handler), myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withValueHandler(handler: Any?): IterationType {
        if (myValueHandler === handler) {
            return this
        }

        return IterationType(myClass, myBindings, mySuperClass, mySuperInterfaces, myIteratedType, handler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withContentValueHandler(handler: Any?): IterationType {
        if (myIteratedType.valueHandler === handler) {
            return this
        }

        return IterationType(myClass, myBindings, mySuperClass, mySuperInterfaces,
                myIteratedType.withValueHandler(handler), myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withStaticTyping(): IterationType {
        if (isUsedAsStaticType) {
            return this
        }

        return IterationType(myClass, myBindings, mySuperClass, mySuperInterfaces, myIteratedType.withStaticTyping(),
                myValueHandler, myTypeHandler, true)
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): IterationType {
        return IterationType(raw, myBindings, superClass, superInterfaces, myIteratedType, myIteratedType,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun buildCanonicalName(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(myClass.qualifiedName)

        if (hasNTypeParameters(1)) {
            stringBuilder.append('<')
            stringBuilder.append(myIteratedType.toCanonical())
            stringBuilder.append('>')
        }

        return stringBuilder.toString()
    }

    /*
     *******************************************************************************************************************
     * Public API overrides
     *******************************************************************************************************************
     */

    override val contentType: KotlinType?
        get() = myIteratedType

    override fun hasContentType(): Boolean {
        return true
    }

    override val isIterationType: Boolean = true

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
        classSignature(myClass, stringBuilder, false)
        stringBuilder.append('<')
        myIteratedType.getErasedSignature(stringBuilder)
        stringBuilder.append(">;")
        return stringBuilder
    }

    companion object {

        /**
         * Factory method that can be used to "upgrade" a basic type into iteration type; usually done via
         * [TypeModifier]
         *
         * @param baseType Resolved non-iteration type (usually [SimpleType]) that is being upgraded
         *
         * @param iteratedType Iterated type; usually the first and only type parameter, but not necessarily
         */
        fun upgradeFrom(baseType: KotlinType, iteratedType: KotlinType): IterationType {
            if (baseType !is TypeBase) {
                throw IllegalArgumentException("Cannot upgrade from an instance of ${baseType::class}")
            }

            return IterationType(baseType, iteratedType)
        }

        fun construct(clazz: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
                superInterfaces: Array<KotlinType>?, iteratedType: KotlinType): IterationType {
            return IterationType(clazz, bindings, superClass, superInterfaces, iteratedType, null, null, false)
        }

    }

}