package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

@Suppress("EqualsOrHashCode")
open class MapLikeType : TypeBase {

    protected val myKeyType: KotlinType

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

    override fun withContentType(contentType: KotlinType): KotlinType {
        TODO("Not yet implemented")
    }

    override fun withTypeHandler(handler: Any?): MapLikeType {
        TODO("Not yet implemented")
    }

    override fun withContentTypeHandler(handler: Any?): MapLikeType {
        TODO("Not yet implemented")
    }

    override fun withValueHandler(handler: Any?): MapLikeType {
        TODO("Not yet implemented")
    }

    override fun withContentValueHandler(handler: Any?): MapLikeType {
        TODO("Not yet implemented")
    }

    override fun withStaticTyping(): MapLikeType {
        TODO("Not yet implemented")
    }

    override fun refine(raw: KClass<*>, bindings: TypeBindings, superClass: KotlinType,
            superInterfaces: Array<KotlinType>): KotlinType? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    override val isContainerType = false

    override fun getErasedSignature(stringBuilder: StringBuilder): StringBuilder {
        TODO("Not yet implemented")
    }

    override fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Standard methods
     *******************************************************************************************************************
     */

    override fun toString(): String {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

}