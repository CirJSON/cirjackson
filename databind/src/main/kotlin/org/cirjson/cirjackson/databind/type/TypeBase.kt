package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.type.WritableTypeID
import org.cirjson.cirjackson.databind.CirJacksonSerializable
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.SerializerProvider
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.util.isInterface
import org.cirjson.cirjackson.databind.util.isPrimitive
import kotlin.reflect.KClass

abstract class TypeBase : KotlinType, CirJacksonSerializable {

    protected val mySuperClass: KotlinType?

    protected val mySuperInterfaces: Array<KotlinType>?

    /**
     * Lazily initialized external representation of the type
     */
    @Volatile
    protected var myCanonicalName: String? = null

    /**
     * Bindings in effect for this type instance; possibly empty. Needed when resolving types declared in members of
     * this type (if any).
     */
    protected val myBindings: TypeBindings

    /**
     * Main constructor to use by extending classes.
     */
    protected constructor(raw: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            interfaces: Array<KotlinType>?, additionalHash: Int, valueHandler: Any?, typeHandler: Any?,
            isUsedAsStaticType: Boolean) : super(raw, additionalHash, valueHandler, typeHandler, isUsedAsStaticType) {
        myBindings = bindings ?: NO_BINDINGS
        mySuperClass = superClass
        mySuperInterfaces = interfaces
    }

    /**
     * Copy-constructor used when refining/upgrading type instances.
     */
    protected constructor(base: TypeBase) : super(base) {
        myBindings = base.myBindings
        mySuperClass = base.mySuperClass
        mySuperInterfaces = base.mySuperInterfaces
    }

    override fun toCanonical(): String {
        return myCanonicalName ?: buildCanonicalName().also { myCanonicalName = it }
    }

    open fun buildCanonicalName(): String {
        return myClass.qualifiedName!!
    }

    override val bindings: TypeBindings
        get() = myBindings

    override fun containedTypeCount(): Int {
        return myBindings.size
    }

    override fun containedType(index: Int): KotlinType? {
        return myBindings.getBoundType(index)
    }

    override val superClass: KotlinType?
        get() = mySuperClass

    override val interfaces: List<KotlinType>
        get() {
            mySuperInterfaces ?: return emptyList()

            return when (mySuperInterfaces.size) {
                0 -> emptyList()
                1 -> listOf(mySuperInterfaces[0])
                else -> mySuperInterfaces.toList()
            }
        }

    final override fun findSuperType(erasedTarget: KClass<*>): KotlinType? {
        if (erasedTarget == myClass) {
            return this
        }

        if (erasedTarget.isInterface && mySuperInterfaces != null) {
            for (superInterface in mySuperInterfaces) {
                val type = superInterface.findSuperType(erasedTarget)

                if (type != null) {
                    return type
                }
            }
        }

        return mySuperClass?.findSuperType(erasedTarget)
    }

    override fun findTypeParameters(expectedType: KClass<*>): Array<KotlinType?> {
        return findSuperType(expectedType)?.bindings?.typeParameterArray() ?: NO_TYPES
    }

    /*
     *******************************************************************************************************************
     * CirJacksonSerializable base implementation
     *******************************************************************************************************************
     */

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider,
            typeSerializer: TypeSerializer) {
        val typeIdDef = WritableTypeID(this, CirJsonToken.VALUE_STRING)
        typeSerializer.writeTypePrefix(generator, serializers, typeIdDef)
        serialize(generator, serializers)
        typeSerializer.writeTypeSuffix(generator, serializers, typeIdDef)
    }

    @Throws(CirJacksonException::class)
    override fun serialize(generator: CirJsonGenerator, serializers: SerializerProvider) {
        generator.writeString(toCanonical())
    }

    /*
     *******************************************************************************************************************
     * Methods for subclasses to use
     *******************************************************************************************************************
     */

    protected open fun hasNTypeParameters(count: Int): Boolean {
        return myClass.typeParameters.size == count
    }

    companion object {

        private val NO_BINDINGS = TypeBindings.EMPTY

        private val NO_TYPES = emptyArray<KotlinType?>()

        /**
         * @param trailingSemicolon Whether to add trailing semicolon for non-primitive (reference) types or not
         */
        fun classSignature(clazz: KClass<*>, stringBuilder: StringBuilder, trailingSemicolon: Boolean): StringBuilder {
            if (clazz.isPrimitive) {
                when (clazz) {
                    Boolean::class -> stringBuilder.append('Z')
                    Byte::class -> stringBuilder.append('B')
                    Short::class -> stringBuilder.append('S')
                    Char::class -> stringBuilder.append('C')
                    Int::class -> stringBuilder.append('I')
                    Long::class -> stringBuilder.append('J')
                    Float::class -> stringBuilder.append('F')
                    Double::class -> stringBuilder.append('D')
                    UInt::class, Void::class -> stringBuilder.append('V')
                    else -> throw IllegalStateException("Unrecognized primitive type: ${clazz.qualifiedName}")
                }

                return stringBuilder
            }

            stringBuilder.append('L')
            val name = clazz.qualifiedName!!

            for (c in name) {
                stringBuilder.append(c.takeUnless { it == '.' } ?: '/')
            }

            if (trailingSemicolon) {
                stringBuilder.append(';')
            }

            return stringBuilder
        }

    }

}