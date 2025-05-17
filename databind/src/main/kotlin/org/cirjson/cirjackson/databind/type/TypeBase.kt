package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.CirJacksonSerializable
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import kotlin.reflect.KClass

abstract class TypeBase : KotlinType, CirJacksonSerializable {

    protected val myBindings: TypeBindings

    protected constructor(raw: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            interfaces: List<KotlinType>?, additionalHash: Int, valueHandler: Any?, typeHandler: Any?,
            isUsedAsStaticType: Boolean) : super(raw, additionalHash, valueHandler, typeHandler, isUsedAsStaticType) {
        myBindings = bindings ?: NO_BINDINGS
    }

    protected constructor(base: TypeBase) : super(base) {
        myBindings = base.myBindings
    }

    override fun toCanonical(): String {
        TODO("Not yet implemented")
    }

    override val bindings: TypeBindings
        get() = myBindings

    override fun containedTypeCount(): Int {
        TODO("Not yet implemented")
    }

    override fun containedType(index: Int): KotlinType? {
        TODO("Not yet implemented")
    }

    override val superClass: KotlinType?
        get() = TODO("Not yet implemented")

    override val interfaces: List<KotlinType>
        get() {
            TODO("Not yet implemented")
        }

    override fun findSuperType(erasedTarget: Class<*>): KotlinType? {
        TODO("Not yet implemented")
    }

    override fun findTypeParameters(expectedType: Class<*>): Array<KotlinType> {
        TODO("Not yet implemented")
    }

    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        TODO("Not yet implemented")
    }

    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider) {
        TODO("Not yet implemented")
    }

    companion object {

        val NO_BINDINGS = TypeBindings.EMPTY

    }

}