package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.AnnotatedClassResolver
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import kotlin.reflect.KClass

/**
 * Standard [SubtypeResolver] implementation.
 */
open class StandardSubtypeResolver : SubtypeResolver {

    protected var myRegisteredSubtypes: MutableSet<NamedType>?

    constructor() : super() {
        myRegisteredSubtypes = null
    }

    constructor(registeredSubtypes: MutableSet<NamedType>?) {
        myRegisteredSubtypes = registeredSubtypes
    }

    override fun snapshot(): SubtypeResolver {
        return myRegisteredSubtypes?.let { StandardSubtypeResolver(LinkedHashSet(it)) } ?: StandardSubtypeResolver()
    }

    /*
     *******************************************************************************************************************
     * Subtype registration
     *******************************************************************************************************************
     */

    override fun registerSubtypes(vararg subtypes: NamedType): StandardSubtypeResolver {
        if (myRegisteredSubtypes == null) {
            myRegisteredSubtypes = LinkedHashSet()
        }

        myRegisteredSubtypes!!.addAll(subtypes)
        return this
    }

    override fun registerSubtypes(vararg subtypes: KClass<*>): StandardSubtypeResolver {
        val types = Array(subtypes.size) { NamedType(subtypes[it]) }
        return registerSubtypes(*types)
    }

    override fun registerSubtypes(subtypes: Collection<KClass<*>>): StandardSubtypeResolver {
        return registerSubtypes(*subtypes.toTypedArray())
    }

    /*
     *******************************************************************************************************************
     * Resolution by class (serialization)
     *******************************************************************************************************************
     */

    override fun collectAndResolveSubtypesByClass(config: MapperConfig<*>, property: AnnotatedMember?,
            baseType: KotlinType?): Collection<NamedType> {
        val annotationIntrospector = config.annotationIntrospector!!

        val rawBase = baseType?.rawClass ?: property?.rawType ?: throw IllegalArgumentException(
                "Both property and base type are nulls")

        val collected = HashMap<NamedType, NamedType>()

        if (myRegisteredSubtypes != null) {
            for (subtype in myRegisteredSubtypes!!) {
                if (rawBase.isAssignableFrom(subtype.type)) {
                    val current = AnnotatedClassResolver.resolveWithoutSuperTypes(config, subtype.type)
                    collectAndResolve(config, current, subtype, annotationIntrospector, collected)
                }
            }
        }

        if (property != null) {
            val subtypes = annotationIntrospector.findSubtypes(config, property)

            if (subtypes != null) {
                for (namedType in subtypes) {
                    val annotatedClass = AnnotatedClassResolver.resolveWithoutSuperTypes(config, namedType.type)
                    collectAndResolve(config, annotatedClass, namedType, annotationIntrospector, collected)
                }
            }
        }

        val rootType = NamedType(rawBase, null)
        val annotatedClass = AnnotatedClassResolver.resolveWithoutSuperTypes(config, rawBase)

        collectAndResolve(config, annotatedClass, rootType, annotationIntrospector, collected)

        return ArrayList(collected.values)
    }

    override fun collectAndResolveSubtypesByClass(config: MapperConfig<*>,
            baseType: AnnotatedClass): Collection<NamedType> {
        val annotationIntrospector = config.annotationIntrospector!!
        val subtypes = HashMap<NamedType, NamedType>()

        if (myRegisteredSubtypes != null) {
            val rawBase = baseType.rawType

            for (subtype in myRegisteredSubtypes!!) {
                if (rawBase.isAssignableFrom(subtype.type)) {
                    val current = AnnotatedClassResolver.resolveWithoutSuperTypes(config, subtype.type)
                    collectAndResolve(config, current, subtype, annotationIntrospector, subtypes)
                }
            }
        }

        val rootType = NamedType(baseType.rawType, null)
        collectAndResolve(config, baseType, rootType, annotationIntrospector, subtypes)
        return ArrayList(subtypes.values)
    }

    /*
     *******************************************************************************************************************
     * Resolution by class (deserialization)
     *******************************************************************************************************************
     */

    override fun collectAndResolveSubtypesByTypeId(config: MapperConfig<*>, property: AnnotatedMember?,
            baseType: KotlinType): Collection<NamedType> {
        val annotationIntrospector = config.annotationIntrospector!!
        val rawBase = baseType.rawClass

        val typesHandled = LinkedHashSet<KClass<*>>()
        val byName = LinkedHashMap<String, NamedType>()

        val rootType = NamedType(rawBase, null)
        var annotatedClass = AnnotatedClassResolver.resolveWithoutSuperTypes(config, rawBase)
        collectAndResolveByTypeId(config, annotatedClass, rootType, typesHandled, byName)

        if (property != null) {
            val subtypes = annotationIntrospector.findSubtypes(config, property)

            if (subtypes != null) {
                for (namedType in subtypes) {
                    annotatedClass = AnnotatedClassResolver.resolveWithoutSuperTypes(config, namedType.type)
                    collectAndResolveByTypeId(config, annotatedClass, namedType, typesHandled, byName)
                }
            }
        }

        if (myRegisteredSubtypes != null) {
            for (subtype in myRegisteredSubtypes!!) {
                if (rawBase.isAssignableFrom(subtype.type)) {
                    val current = AnnotatedClassResolver.resolveWithoutSuperTypes(config, subtype.type)
                    collectAndResolveByTypeId(config, current, subtype, typesHandled, byName)
                }
            }
        }

        return combineNamedAndUnnamed(rawBase, typesHandled, byName)
    }

    override fun collectAndResolveSubtypesByTypeId(config: MapperConfig<*>,
            baseType: AnnotatedClass): Collection<NamedType> {
        val rawBase = baseType.rawType
        val typesHandled = LinkedHashSet<KClass<*>>()
        val byName = LinkedHashMap<String, NamedType>()

        val rootType = NamedType(rawBase, null)
        collectAndResolveByTypeId(config, baseType, rootType, typesHandled, byName)

        if (myRegisteredSubtypes != null) {
            for (subtype in myRegisteredSubtypes!!) {
                if (rawBase.isAssignableFrom(subtype.type)) {
                    val current = AnnotatedClassResolver.resolveWithoutSuperTypes(config, subtype.type)
                    collectAndResolveByTypeId(config, current, subtype, typesHandled, byName)
                }
            }
        }

        return combineNamedAndUnnamed(rawBase, typesHandled, byName)
    }

    /*
     *******************************************************************************************************************
     * Internal methods
     *******************************************************************************************************************
     */

    /**
     * Method called to find subtypes for a specific type (class), using type (class) as the unique key (in case of
     * conflicts).
     */
    protected open fun collectAndResolve(config: MapperConfig<*>, annotatedType: AnnotatedClass, namedType: NamedType,
            annotationIntrospector: AnnotationIntrospector, collectedSubtypes: HashMap<NamedType, NamedType>) {
        var realNamedType = namedType

        if (!realNamedType.hasName()) {
            val name = annotationIntrospector.findTypeName(config, annotatedType)

            if (name != null) {
                realNamedType = NamedType(realNamedType.type, name)
            }
        }

        val typeOnlyNamedType = NamedType(realNamedType.type)

        if (collectedSubtypes.containsKey(typeOnlyNamedType)) {
            if (realNamedType.hasName()) {
                val prev = collectedSubtypes[typeOnlyNamedType]!!

                if (!prev.hasName()) {
                    collectedSubtypes[typeOnlyNamedType] = realNamedType
                }
            }

            return
        }

        collectedSubtypes[typeOnlyNamedType] = realNamedType
        val subtypes = annotationIntrospector.findSubtypes(config, annotatedType)

        if (subtypes.isNullOrEmpty()) {
            return
        }

        for (subtype in subtypes) {
            val subtypeClass = AnnotatedClassResolver.resolveWithoutSuperTypes(config, subtype.type)
            collectAndResolve(config, subtypeClass, subtype, annotationIntrospector, collectedSubtypes)
        }
    }

    /**
     * Method called to find subtypes for a specific type (class), using type id as the unique key (in case of
     * conflicts).
     */
    protected open fun collectAndResolveByTypeId(config: MapperConfig<*>, annotatedType: AnnotatedClass,
            namedType: NamedType, typesHandled: MutableSet<KClass<*>>, byName: MutableMap<String, NamedType>) {
        var realNamedType = namedType
        val annotationIntrospector = config.annotationIntrospector!!

        if (!realNamedType.hasName()) {
            val name = annotationIntrospector.findTypeName(config, annotatedType)

            if (name != null) {
                realNamedType = NamedType(realNamedType.type, name)
            }
        }

        if (realNamedType.hasName()) {
            byName[realNamedType.name!!] = realNamedType
        }

        if (!typesHandled.add(namedType.type)) {
            return
        }

        val subtypes = annotationIntrospector.findSubtypes(config, annotatedType)

        if (subtypes.isNullOrEmpty()) {
            return
        }

        for (subtype in subtypes) {
            val subtypeClass = AnnotatedClassResolver.resolveWithoutSuperTypes(config, subtype.type)
            collectAndResolveByTypeId(config, subtypeClass, subtype, typesHandled, byName)
        }
    }

    /**
     * Helper method used for merging explicitly named types and handled classes without explicit names.
     */
    protected open fun combineNamedAndUnnamed(rawBase: KClass<*>, typesHandled: MutableSet<KClass<*>>,
            byName: MutableMap<String, NamedType>): Collection<NamedType> {
        val result = ArrayList(byName.values)

        for (namedType in byName.values) {
            typesHandled.remove(namedType.type)
        }

        for (clazz in typesHandled) {
            if (clazz == rawBase && clazz.isAbstract) {
                continue
            }

            result.add(NamedType(clazz))
        }

        return result
    }

}