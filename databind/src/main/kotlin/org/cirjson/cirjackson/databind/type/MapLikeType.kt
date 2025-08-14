package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * Type that represents Map-like types; things that consist of key/value pairs but that do not necessarily implement
 * [Map], but that do not have enough introspection functionality to allow for some level of generic handling. This
 * specifically allows framework to check for configuration and annotation settings used for Map types, and pass these
 * to custom handlers that may be more familiar with actual type.
 */
@Suppress("EqualsOrHashCode")
open class MapLikeType : TypeBase {

    /**
     * Type of keys of Map.
     */
    protected val myKeyType: KotlinType

    /**
     * Type of values of Map.
     */
    protected val myValueType: KotlinType

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(mapType: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            interfaces: Array<KotlinType>?, keyType: KotlinType, valueType: KotlinType, valueHandler: Any?,
            typeHandler: Any?, isUsedAsStaticType: Boolean) : super(mapType, bindings, superClass, interfaces,
            31 * keyType.hashCode() + valueType.hashCode(), valueHandler, typeHandler, isUsedAsStaticType) {
        myKeyType = keyType
        myValueType = valueType
    }

    protected constructor(base: TypeBase, keyType: KotlinType, valueType: KotlinType) : super(base) {
        myKeyType = keyType
        myValueType = valueType
    }

    open fun withKeyType(keyType: KotlinType): KotlinType {
        if (myKeyType === keyType) {
            return this
        }

        return MapLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, keyType, myValueType, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withContentType(contentType: KotlinType): KotlinType {
        if (myValueType === contentType) {
            return this
        }

        return MapLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType, contentType, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withTypeHandler(handler: Any?): MapLikeType {
        return MapLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType, myValueType, myValueHandler,
                handler, isUsedAsStaticType)
    }

    override fun withContentTypeHandler(handler: Any?): MapLikeType {
        return MapLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType,
                myValueType.withTypeHandler(handler), myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withValueHandler(handler: Any?): MapLikeType {
        return MapLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType, myValueType, handler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun withContentValueHandler(handler: Any?): MapLikeType {
        return MapLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType,
                myValueType.withValueHandler(handler), myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    override fun withHandlersFrom(src: KotlinType): KotlinType {
        var type = super.withHandlersFrom(src)
        val srcKeyType = src.keyType

        if (type is MapLikeType) {
            if (srcKeyType != null) {
                val contentType = myKeyType.withHandlersFrom(srcKeyType)

                if (contentType !== myKeyType) {
                    type = type.withKeyType(contentType)
                }
            }
        }

        val srcContentType = src.contentType ?: return type
        val contentType = myValueType.withHandlersFrom(srcContentType)
        return type.takeUnless { contentType === myValueType } ?: type.withContentType(contentType)
    }

    override fun withStaticTyping(): MapLikeType {
        if (isUsedAsStaticType) {
            return this
        }

        return MapLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType,
                myValueType.withStaticTyping(), myValueHandler, myTypeHandler, true)
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings, superClass: KotlinType,
            superInterfaces: Array<KotlinType>): KotlinType {
        return MapLikeType(raw, bindings, superClass, superInterfaces, myKeyType, myValueType, myValueHandler,
                myTypeHandler, isUsedAsStaticType)
    }

    override fun buildCanonicalName(): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append(myClass.qualifiedName)

        if (hasNTypeParameters(2)) {
            stringBuilder.append('<')
            stringBuilder.append(myKeyType.toCanonical())
            stringBuilder.append(',')
            stringBuilder.append(myValueType.toCanonical())
            stringBuilder.append('>')
        }

        return stringBuilder.toString()
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    override val isContainerType = true

    override val isMapLikeType: Boolean = true

    override val keyType: KotlinType
        get() = myKeyType

    override val contentType: KotlinType
        get() = myValueType

    override val contentValueHandler: Any?
        get() = myValueType.valueHandler

    override val contentTypeHandler: Any?
        get() = myValueType.typeHandler

    override fun hasHandlers(): Boolean {
        return super.hasHandlers() || myValueType.hasHandlers() || myKeyType.hasHandlers()
    }

    override fun getErasedSignature(stringBuilder: StringBuilder): StringBuilder {
        return classSignature(myClass, stringBuilder, true)
    }

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
        classSignature(myClass, stringBuilder, false)
        stringBuilder.append('<')
        myKeyType.getGenericSignature(stringBuilder)
        myValueType.getGenericSignature(stringBuilder)
        stringBuilder.append(">;")
        return stringBuilder
    }

    /*
     *******************************************************************************************************************
     * Extended API
     *******************************************************************************************************************
     */

    open fun withKeyTypeHandler(handler: Any?): MapLikeType {
        return MapLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType.withTypeHandler(handler),
                myValueType, myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    open fun withKeyValueHandler(handler: Any?): MapLikeType {
        return MapLikeType(myClass, myBindings, mySuperClass, mySuperInterfaces, myKeyType.withValueHandler(handler),
                myValueType, myValueHandler, myTypeHandler, isUsedAsStaticType)
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "[map-like type; class ${myClass.qualifiedName}, $myKeyType -> $myValueType]"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is MapLikeType || other::class != this::class) {
            return false
        }

        return myClass == other.myClass && myKeyType == other.myKeyType && myValueType == other.myValueType
    }

    companion object {

        /**
         * Factory method that can be used to "upgrade" a basic type into collection-like one; usually done via
         * [TypeModifier]
         */
        fun upgradeFrom(baseType: KotlinType, keyType: KotlinType, valueType: KotlinType): MapLikeType {
            if (baseType !is TypeBase) {
                throw IllegalArgumentException("Cannot upgrade from an instance of ${baseType::class}")
            }

            return MapLikeType(baseType, keyType, valueType)
        }

    }

}