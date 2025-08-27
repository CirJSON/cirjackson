package org.cirjson.cirjackson.databind.cirjsontype

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.KotlinType
import kotlin.reflect.KClass

/**
 * Interface that defines standard API for converting types to type identifiers and vice versa. Used by type resolvers
 * ([TypeSerializer], [TypeDeserializer]) for converting between type and matching id; id is stored in CirJSON and
 * needed for creating instances of proper subtypes when deserializing values.
 *
 * NOTE: it is **strongly** recommended that developers always extend abstract base class
 * [org.cirjson.cirjackson.databind.cirjsontype.implementation.TypeIdResolverBase] instead of directly implementing this
 * interface; this helps prevent breakage in case new methods need to be added in this interface (something we try to
 * avoid doing; but which may be necessary in some cases).
 */
interface TypeIdResolver {

    /**
     * Method that will be called once before any type resolution calls; used to initialize instance with configuration.
     * This is necessary since instances may be created via reflection, without ability to call specific constructor to
     * pass in configuration settings.
     *
     * @param type Base type for which this id resolver instance is used
     */
    @Throws(CirJacksonException::class)
    fun init(type: KotlinType)

    /**
     * Method called to serialize type of the type of given value as a String to include in serialized CirJSON content.
     */
    @Throws(CirJacksonException::class)
    fun idFromValue(context: DatabindContext, value: Any?): String?

    /**
     * Alternative method used for determining type from combination of value and type, using suggested type (that
     * serializer provides) and possibly value of that type. Most common implementation will use suggested type as is.
     */
    @Throws(CirJacksonException::class)
    fun idFromValueAndType(context: DatabindContext, value: Any?, suggestedType: KClass<*>?): String?

    /**
     * Method that can be called to figure out type id to use for instances of base type (declared type of property).
     * This is usually only used for fallback handling, for cases where real type information is not available for some
     * reason.
     */
    @Throws(CirJacksonException::class)
    fun idFromBaseType(context: DatabindContext): String?

    /**
     * Method called to resolve type from given type identifier.
     */
    @Throws(CirJacksonException::class)
    fun typeFromId(context: DatabindContext, id: String): KotlinType?

    /**
     * Accessor called for error-reporting and diagnostics purposes.
     */
    val descriptionForKnownTypeIds: String?

    /**
     * Accessor for mechanism that this resolver uses for determining type id from type. Mostly informational; not
     * required to be called or used.
     */
    val mechanism: CirJsonTypeInfo.Id

}