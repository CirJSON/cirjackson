package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver

/**
 * Partial base implementation of [TypeIdResolver]: all custom implementations are **strongly** recommended to extend
 * this class, instead of directly implementing [TypeIdResolver]. Note that ALL subclass need to re-implement
 * [typeFromId] method; otherwise implementation will not work.
 *
 * Note that instances created to be constructed from annotations
 * ([org.cirjson.cirjackson.databind.annotation.CirJsonTypeIdResolver]) are always created using no-arguments
 * constructor; protected constructor is only used subclasses.
 *
 * @property myBaseType Common base type for all polymorphic instances handled.
 */
abstract class TypeIdResolverBase(protected val myBaseType: KotlinType?) : TypeIdResolver {

    constructor() : this(null)

    override fun init(type: KotlinType) {
        // No-op
    }

    override fun idFromBaseType(context: DatabindContext): String? {
        return idFromValueAndType(context, null, myBaseType!!.rawClass)
    }

    @Throws(CirJacksonException::class)
    override fun typeFromId(context: DatabindContext, id: String): KotlinType? {
        throw IllegalStateException(
                "Subclass ${this::class.qualifiedName} MUST implement `typeFromId(DatabindContext,String)")
    }

    override val descriptionForKnownTypeIds: String?
        get() = null

}