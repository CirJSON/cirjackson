package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.annotations.CirJsonTypeInfo
import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.MapperFeature
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * [org.cirjson.cirjackson.databind.cirjsontype.TypeIdResolver] implementation that converts using explicitly
 * (annotation-) specified type names and maps to implementation classes; or, in absence of annotated type name,
 * defaults to fully-qualified [KClass] names (obtained with [KClass.qualifiedName]
 *
 * @property myTypeToId Mappings from class name to type id, used for serialization.
 *
 * Since lazily constructed will require synchronization (either internal by type, or external)
 *
 * @property myIdToType Mappings from type id to KotlinType, used for deserialization.
 *
 * Eagerly constructed, not modified, can use regular unsynchronized [Map].
 */
open class TypeNameIdResolver protected constructor(baseType: KotlinType,
        protected val myTypeToId: ConcurrentHashMap<String, String>, protected val myIdToType: Map<String, KotlinType>?,
        protected val myCaseInsensitive: Boolean) : TypeIdResolverBase(baseType) {

    override val mechanism: CirJsonTypeInfo.Id
        get() = CirJsonTypeInfo.Id.SIMPLE_NAME

    override fun idFromValue(context: DatabindContext, value: Any?): String? {
        return idFromClass(context, value!!::class)
    }

    protected open fun idFromClass(context: DatabindContext, clazz: KClass<*>?): String? {
        clazz ?: return null
        val key = clazz.qualifiedName!!
        var name = myTypeToId[key]

        if (name == null) {
            if (context.isAnnotationProcessingEnabled) {
                name = context.annotationIntrospector!!.findTypeName(context.config,
                        context.introspectClassAnnotations(clazz))
            }

            if (name == null) {
                name = defaultTypeId(clazz)
            }

            myTypeToId[key] = name
        }

        return name
    }

    override fun idFromValueAndType(context: DatabindContext, value: Any?, suggestedType: KClass<*>?): String? {
        return value?.let { idFromValue(context, it) } ?: idFromClass(context, suggestedType)
    }

    override fun typeFromId(context: DatabindContext, id: String): KotlinType? {
        return typeFromId(id)
    }

    protected open fun typeFromId(id: String): KotlinType? {
        return myIdToType!![id.takeUnless { myCaseInsensitive } ?: id.lowercase()]
    }

    override val descriptionForKnownTypeIds: String?
        get() {
            val ids = TreeSet<String>()

            for ((key, value) in myIdToType!!) {
                if (value.isConcrete) {
                    ids.add(key)
                }
            }

            return ids.toString()
        }

    companion object {

        fun construct(config: MapperConfig<*>, baseType: KotlinType, subtypes: Collection<NamedType>?,
                forSerialization: Boolean, forDeserialization: Boolean): TypeNameIdResolver {
            if (forSerialization == forDeserialization) {
                throw IllegalArgumentException()
            }

            val typeToId: ConcurrentHashMap<String, String>
            val idToType: HashMap<String, KotlinType>?

            if (forSerialization) {
                typeToId = ConcurrentHashMap()
                idToType = null
            } else {
                typeToId = ConcurrentHashMap(4)
                idToType = HashMap()
            }

            val caseInsensitive = config.isEnabled(MapperFeature.ACCEPT_CASE_INSENSITIVE_VALUES)

            subtypes ?: return TypeNameIdResolver(baseType, typeToId, idToType, caseInsensitive)

            for (subtype in subtypes) {
                val clazz = subtype.type
                var id = if (subtype.hasName()) subtype.name!! else defaultTypeId(clazz)

                if (forSerialization) {
                    typeToId[clazz.qualifiedName!!] = id
                }

                if (forDeserialization) {
                    if (caseInsensitive) {
                        id = id.lowercase()
                    }

                    val previous = idToType!![id]

                    if (previous != null) {
                        if (clazz.isAssignableFrom(previous.rawClass)) {
                            continue
                        }
                    }

                    idToType[id] = config.constructType(clazz)
                }
            }

            return TypeNameIdResolver(baseType, typeToId, idToType, caseInsensitive)
        }

        /**
         * If no name was explicitly given for a class, we will just use simple class name
         */
        fun defaultTypeId(clazz: KClass<*>): String {
            val name = clazz.qualifiedName!!
            val index = name.lastIndexOf('.')
            return name.takeUnless { index < 0 } ?: name.substring(index + 1)
        }

    }

}