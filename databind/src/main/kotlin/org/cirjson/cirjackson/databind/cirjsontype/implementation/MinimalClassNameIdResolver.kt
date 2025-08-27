package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.PolymorphicTypeValidator

/**
 * Specialization of [ClassNameIdResolver] that instead uses a "minimal" derivation of [kotlin.reflect.KClass] name,
 * using relative reference from the base type (base class) that polymorphic value has.
 */
open class MinimalClassNameIdResolver(baseType: KotlinType, polymorphicTypeValidator: PolymorphicTypeValidator) :
        ClassNameIdResolver(baseType, polymorphicTypeValidator) {

    /**
     * Package name of the base class, to be used for determining common prefix that can be omitted from included type
     * id. Does not include the trailing dot.
     */
    protected val myBasePackageName: String

    /**
     * Same as [myBasePackageName], but includes trailing dot.
     */
    protected val myBasePackagePrefix: String

    init {
        val base = baseType.rawClass.qualifiedName!!
        val index = base.lastIndexOf('.')

        if (index < 0) {
            myBasePackageName = ""
            myBasePackagePrefix = "."
        } else {
            myBasePackageName = base.take(index)
            myBasePackagePrefix = base.take(index + 1)
        }
    }

    override val mechanism: CirJsonTypeInfo.Id
        get() = CirJsonTypeInfo.Id.MINIMAL_CLASS

    override fun idFromValue(context: DatabindContext, value: Any?): String? {
        val name = value!!::class.qualifiedName!!

        return name.takeUnless { it.startsWith(myBasePackagePrefix) } ?: name.substring(myBasePackagePrefix.lastIndex)
    }

    override fun internalTypeFromId(context: DatabindContext, id: String): KotlinType? {
        if (!id.startsWith(".")) {
            return super.internalTypeFromId(context, id)
        }

        val stringBuilder = StringBuilder(id.length + myBasePackageName.length)

        if (myBasePackageName.isEmpty()) {
            stringBuilder.append(id.substring(1))
        } else {
            stringBuilder.append(myBasePackageName).append(id)
        }

        return super.internalTypeFromId(context, id)
    }

    companion object {

        fun construct(baseType: KotlinType,
                polymorphicTypeValidator: PolymorphicTypeValidator): MinimalClassNameIdResolver {
            return MinimalClassNameIdResolver(baseType, polymorphicTypeValidator)
        }

    }

}