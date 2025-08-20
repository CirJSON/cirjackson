package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.*
import java.lang.reflect.*
import java.util.*
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.*
import kotlin.reflect.KClass

/**
 * Class used for creating concrete [KotlinType] instances, given various inputs.
 *
 * Instances of this class are accessible using [org.cirjson.cirjackson.databind.ObjectMapper] as well as many objects
 * it constructs (like [org.cirjson.cirjackson.databind.DeserializationConfig] and
 * [org.cirjson.cirjackson.databind.SerializationConfig]), but usually those objects also expose convenience methods
 * (`constructType`). So, you can do for example:
 * ```
 * val stringType = mapper.constructType(String::class)
 * ```
 * However, more advanced methods are only exposed by factory so that you may need to use:
 * ```
 * val stringCollection = mapper.typeFactory.constructCollectionType(List::class, String::class)
 * ```
 *
 * Note on optimizations: generic type parameters are resolved for all types, with following
 * exceptions:
 *
 * * For optimization purposes, type resolution is skipped for following commonly seen types that do have type
 * parameters, but ones that are rarely needed:
 *     * [Enum]: Self-referential type reference is simply dropped and Class is exposed as a simple, non-parameterized
 *     [SimpleType]
 *     * [Comparable]: Type parameter is simply dropped and interface is exposed as a simple, non-parameterized
 *     [SimpleType]
 * * For [Collection] subtypes, resolved type is ALWAYS the parameter for [Collection] and not that of actually resolved
 * subtype. This is usually (but not always) same parameter.
 * * For [Map] subtypes, resolved type is ALWAYS the parameter for [Map] and not that of actually resolved subtype.
 * These are usually (but not always) same parameters.
 *
 * @property myTypeCache Since type resolution can be expensive (specifically when resolving actual generic types), we
 * will use small cache to avoid repetitive resolution of core types
 *
 * @property myModifiers Registered [TypeModifiers][TypeModifier]: objects that can change details of [KotlinType]
 * instances factory constructs.
 *
 * @property classLoader ClassLoader used by this factory.
 */
class TypeFactory private constructor(internal val myTypeCache: LookupCache<Any, KotlinType>,
        internal val myModifiers: Array<TypeModifier>?, val classLoader: ClassLoader?) : Snapshottable<TypeFactory> {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    private constructor() : this(SimpleLookupCache(16, DEFAULT_MAX_CACHE_SIZE))

    private constructor(typeCache: LookupCache<Any, KotlinType>) : this(typeCache, null, null)

    /**
     * Need to make a copy on `snapshot()` to avoid accidental leakage via cache. In theory only needed if there are
     * modifiers, but since these are lightweight objects, let's recreate always.
     */
    override fun snapshot(): TypeFactory {
        return TypeFactory(myTypeCache.snapshot(), myModifiers, classLoader)
    }

    /**
     * "Mutant factory" method which will construct a new instance with specified [TypeModifier] added as the first
     * modifier to call (in case there are multiple registered).
     */
    fun withModifier(modifier: TypeModifier?): TypeFactory {
        var typeCache = myTypeCache
        val mods = if (modifier == null) {
            typeCache = typeCache.emptyCopy()
            null
        } else if (myModifiers == null) {
            typeCache = typeCache.emptyCopy()
            arrayOf(modifier)
        } else {
            ArrayBuilders.insertInListNoDup(myModifiers, modifier)
        }

        return TypeFactory(typeCache, mods, classLoader)
    }

    /**
     * "Mutant factory" method which will construct a new instance with specified [ClassLoader] to use by [findClass].
     */
    fun withClassLoader(classLoader: ClassLoader?): TypeFactory {
        return TypeFactory(myTypeCache, myModifiers, classLoader)
    }

    /**
     * Mutant factory method that will construct new [TypeFactory] with identical settings except for different cache.
     */
    fun withCache(cache: LookupCache<Any, KotlinType>): TypeFactory {
        return TypeFactory(cache, myModifiers, classLoader)
    }

    /**
     * Method that will clear up any cached type definitions that may be cached by this [TypeFactory] instance. This
     * method should not be commonly used, that is, only use it if you know there is a problem with retention of type
     * definitions; the most likely (and currently only known) problem is retention of [KClass] instances via
     * [KotlinType] reference.
     */
    fun clearCache() {
        myTypeCache.clear()
    }

    /*
     *******************************************************************************************************************
     * Low-level helper methods
     *******************************************************************************************************************
     */

    @Throws(ClassNotFoundException::class)
    fun findClass(className: String): KClass<*> {
        if ('.' in className) {
            val primitive = findPrimitive(className)

            if (primitive != null) {
                return primitive
            }
        }

        var problem: Throwable? = null
        val loader = classLoader ?: Thread.currentThread().contextClassLoader

        if (loader != null) {
            try {
                return classForName(className, loader)
            } catch (e: Exception) {
                problem = e.rootCause
            }
        }

        try {
            return classForName(className)
        } catch (e: Exception) {
            if (problem == null) {
                problem = e.rootCause
            }
        }

        problem.throwIfRuntimeException()
        throw ClassNotFoundException(problem.message, problem)
    }

    @Throws(ClassNotFoundException::class)
    internal fun classForName(name: String, loader: ClassLoader): KClass<*> {
        return Class.forName(name, true, loader).kotlin
    }

    @Throws(ClassNotFoundException::class)
    internal fun classForName(name: String): KClass<*> {
        return Class.forName(name).kotlin
    }

    private fun findPrimitive(className: String): KClass<*>? {
        return when (className) {
            "int" -> Int::class
            "long" -> Long::class
            "float" -> Float::class
            "double" -> Double::class
            "boolean" -> Boolean::class
            "byte" -> Byte::class
            "char" -> Char::class
            "short" -> Short::class
            "void" -> Void::class
            else -> null
        }
    }

    /*
     *******************************************************************************************************************
     * Type conversion, parameterization resolution methods
     *******************************************************************************************************************
     */

    /**
     * Factory method for creating a subtype of given base type, as defined by specified subclass; but retaining generic
     * type information if any. Can be used, for example, to get equivalent of `HashMap<String, Int>` from
     * `Map<String, Int>` by giving `HashMap::class` as subclass. Shortcut for:
     * ```
     * constructSpecializedType(baseType, subclass, false);
     * ```
     * that is, will use "strict" compatibility checking, usually used for deserialization purposes (but often not for
     * serialization).
     *
     * @param baseType Declared base type with resolved type parameters
     *
     * @param subclass Runtime subtype to use for resolving
     *
     * @return Resolved subtype
     */
    @Throws(IllegalArgumentException::class)
    fun constructSpecializedType(baseType: KotlinType, subclass: KClass<*>): KotlinType {
        return constructSpecializedType(baseType, subclass, false)
    }

    /**
     * Factory method for creating a subtype of given base type, as defined by specified subclass; but retaining generic
     * type information if any. Can be used, for example, to get equivalent of `HashMap<String, Int>` from
     * `Map<String, Int>` by giving `HashMap::class` as subclass.
     *
     * @param baseType Declared base type with resolved type parameters
     *
     * @param subclass Runtime subtype to use for resolving
     *
     * @param relaxedCompatibilityCheck Whether checking for type-assignment compatibility should be "relaxed"
     * (`true`) or "strict" (`false`): typically serialization uses relaxed, deserialization strict checking.
     *
     * @return Resolved subtype
     */
    @Throws(IllegalArgumentException::class)
    fun constructSpecializedType(baseType: KotlinType, subclass: KClass<*>,
            relaxedCompatibilityCheck: Boolean): KotlinType {
        val rawBase = baseType.rawClass

        if (rawBase == subclass) {
            return baseType
        }

        val newType: KotlinType

        if (rawBase == Any::class) {
            newType = fromClass(null, subclass, EMPTY_BINDINGS)
            return newType.withHandlersFrom(baseType)
        }

        if (!rawBase.isAssignableFrom(subclass)) {
            throw IllegalArgumentException("Class ${subclass.qualifiedName} not subtype of ${baseType.typeDescription}")
        }

        if (baseType.isContainerType) {
            if (baseType.isMapLikeType) {
                if (subclass == HashMap::class || subclass == LinkedHashMap::class || subclass == EnumMap::class ||
                        subclass == TreeMap::class) {
                    newType = fromClass(null, subclass,
                            TypeBindings.create(subclass, baseType.keyType!!, baseType.contentType!!))
                    return newType.withHandlersFrom(baseType)
                }
            } else if (baseType.isCollectionLikeType) {
                if (subclass == ArrayList::class || subclass == LinkedList::class || subclass == HashSet::class ||
                        subclass == TreeSet::class) {
                    newType = fromClass(null, subclass, TypeBindings.create(subclass, baseType.contentType!!))
                    return newType.withHandlersFrom(baseType)
                }

                if (rawBase == EnumSet::class) {
                    return baseType
                }
            }
        }

        if (baseType.bindings.isEmpty()) {
            newType = fromClass(null, subclass, EMPTY_BINDINGS)
            return newType.withHandlersFrom(baseType)
        }

        val typeParametersCount = subclass.typeParameters.size

        if (typeParametersCount == 0) {
            newType = fromClass(null, subclass, EMPTY_BINDINGS)
            return newType.withHandlersFrom(baseType)
        }

        val typeBindings = bindingsForSubtype(baseType, typeParametersCount, subclass, relaxedCompatibilityCheck)
        newType = fromClass(null, subclass, typeBindings)
        return newType.withHandlersFrom(baseType)
    }

    @Suppress("UNCHECKED_CAST")
    private fun bindingsForSubtype(baseType: KotlinType, typeParametersCount: Int, subclass: KClass<*>,
            relaxedCompatibilityCheck: Boolean): TypeBindings {
        val placeholders = Array(typeParametersCount) { PlaceholderForType(it) }

        val bindings = TypeBindings.create(subclass, Array(typeParametersCount) { placeholders[it] })
        val tempSub = fromClass(null, subclass, bindings)
        val baseWithPlaceholders = tempSub.findSuperType(baseType.rawClass) ?: throw IllegalArgumentException(
                "Internal error: unable to locate supertype (${baseType.rawClass.qualifiedName}) from resolved subtype ${subclass.qualifiedName}")

        val error = resolveTypePlaceholders(baseType, baseWithPlaceholders)

        if (error != null) {
            if (!relaxedCompatibilityCheck) {
                throw IllegalArgumentException(
                        "Failed to specialize base type ${baseType.toCanonical()} as ${subclass.qualifiedName}, problem: $error")
            }
        }

        return TypeBindings.create(subclass,
                Array(typeParametersCount) { placeholders[it].actualType() ?: TypeFactory.unknownType() })
    }

    @Throws(IllegalArgumentException::class)
    private fun resolveTypePlaceholders(sourceType: KotlinType, actualType: KotlinType): String? {
        val expectedTypes = sourceType.bindings.typeParameters
        val actualTypes = actualType.bindings.typeParameters

        for (i in expectedTypes.indices) {
            val expected = expectedTypes[i]
            val actual = actualTypes.getOrNull(i) ?: TypeFactory.unknownType()

            if (!verifyAndResolvePlaceholders(expected, actual)) {
                if (expected.hasRawClass(Any::class)) {
                    continue
                }

                if (i == 0 && sourceType.isMapLikeType && actual.hasRawClass(Any::class)) {
                    continue
                }

                if (expected.isInterface && expected.isTypeOrSuperTypeOf(actual.rawClass)) {
                    continue
                }

                return "Type parameter #${i + 1}/${expectedTypes.size} differs; can not specialize ${expected.toCanonical()} with ${actual.toCanonical()}"
            }
        }

        return null
    }

    @Throws(IllegalArgumentException::class)
    private fun verifyAndResolvePlaceholders(expected: KotlinType, actual: KotlinType): Boolean {
        if (actual is PlaceholderForType) {
            actual.actualType(expected)
            return true
        }

        if (expected.rawClass != actual.rawClass) {
            return false
        }

        val expectedTypes = expected.bindings.typeParameters
        val actualTypes = actual.bindings.typeParameters

        for ((i, expectedTypeParameter) in expectedTypes.withIndex()) {
            val actualTypeParameter = actualTypes.get(i)

            if (!verifyAndResolvePlaceholders(expectedTypeParameter, actualTypeParameter)) {
                return false
            }
        }

        return true
    }

    /**
     * Method similar to [constructSpecializedType], but that creates a less-specific type of given type. Usually this
     * is as simple as simply finding supertype with type erasure of `superClass`, but there may be need for some
     * additional workarounds.
     */
    fun constructGeneralizedType(baseType: KotlinType, superClass: KClass<*>): KotlinType {
        val rawBase = baseType.rawClass

        if (rawBase == superClass) {
            return baseType
        }

        val supertype = baseType.findSuperType(superClass)

        if (supertype == null) {
            if (!superClass.isAssignableFrom(rawBase)) {
                throw IllegalArgumentException("Class ${superClass.qualifiedName} not a super-type of $baseType")
            }

            throw IllegalArgumentException(
                    "Internal error: class ${superClass.qualifiedName} not included as super-type for $baseType")
        }

        return supertype
    }

    /**
     * Factory method for constructing a [KotlinType] out of its canonical representation (see
     * [KotlinType.toCanonical]).
     *
     * @param canonical Canonical string representation of a type
     *
     * @throws IllegalArgumentException If canonical representation is malformed, or class that type represents
     * (including its generic parameters) is not found
     */
    @Throws(IllegalArgumentException::class)
    fun constructFromCanonical(canonical: String): KotlinType {
        return TypeParser.INSTANCE.parse(this, canonical)
    }

    /**
     * Method that is to figure out actual type parameters that given class binds to generic types defined by given
     * (generic) interface or class. This could mean, for example, trying to figure out key and value types for Map
     * implementations.
     *
     * @param type Subtype (leaf type) that implements `expectedType`
     */
    fun findTypeParameters(type: KotlinType, expectedType: KClass<*>): Array<KotlinType?> {
        return type.findSuperType(expectedType)?.bindings?.typeParameterArray() ?: NO_TYPES.nullable()
    }

    /**
     * Specialized alternative to [findTypeParameters]
     */
    fun findFirstTypeParameters(type: KotlinType, expectedType: KClass<*>): KotlinType {
        return type.findSuperType(expectedType)?.bindings?.getBoundTypeOrNull(0) ?: TypeFactory.unknownType()
    }

    /**
     * Method that can be called to figure out more specific of two types (if they are related; that is, one implements
     * or extends the other); or if not related, return the primary type.
     *
     * @param type1 Primary type to consider
     *
     * @param type2 Secondary type to consider
     */
    fun moreSpecificType(type1: KotlinType?, type2: KotlinType?): KotlinType? {
        type1 ?: return type2
        type2 ?: return type1

        val raw1 = type1.rawClass
        val raw2 = type2.rawClass

        if (raw1 == raw2) {
            return type1
        }

        if (raw1.isAssignableFrom(raw2)) {
            return type2
        }

        return type1
    }

    /*
     *******************************************************************************************************************
     * Public general-purpose factory methods
     *******************************************************************************************************************
     */

    fun constructType(type: Type): KotlinType {
        return fromAny(null, type, EMPTY_BINDINGS)
    }

    fun constructType(typeReference: TypeReference<*>): KotlinType {
        return fromAny(null, typeReference.type, EMPTY_BINDINGS)
    }

    /**
     * Method to call when resolving types of [Members][java.lang.reflect.Member] like Fields, Methods and Constructor
     * parameters and there is a [TypeBindings] (that describes binding of type parameters within context) to pass. This
     * is typically used only by code in databind itself.
     *
     * @param type Type of [java.lang.reflect.Member] to resolve
     *
     * @param contextBindings Type bindings from the context, often class in which member declared but may be subtype of
     * that type (to bind actual bound type parameters). Not used if `type` is of type `KClass<?>`.
     *
     * @return Fully resolved type
     */
    fun resolveMemberType(type: Type, contextBindings: TypeBindings?): KotlinType {
        return fromAny(null, type, contextBindings)
    }

    /*
     *******************************************************************************************************************
     * Direct factory methods
     *******************************************************************************************************************
     */

    /**
     * Method for constructing an [ArrayType].
     *
     * NOTE: type modifiers are NOT called on array type itself; but are called for element type (and other contained
     * types)
     */
    fun constructArrayType(elementType: KClass<*>): ArrayType {
        return ArrayType.construct(fromAny(null, elementType.java, null), null)
    }

    /**
     * Method for constructing an [ArrayType].
     *
     * NOTE: type modifiers are NOT called on array type itself; but are called for contained types.
     */
    fun constructArrayType(elementType: KotlinType): ArrayType {
        return ArrayType.construct(elementType, null)
    }

    /**
     * Method for constructing a [CollectionType].
     *
     * NOTE: type modifiers are NOT called on Collection type itself; but are called for contained types.
     */
    fun constructCollectionType(collectionClass: KClass<out Collection<*>>, elementClass: KClass<*>): CollectionType {
        return constructCollectionType(collectionClass, fromClass(null, elementClass, EMPTY_BINDINGS))
    }

    /**
     * Method for constructing a [CollectionType].
     *
     * NOTE: type modifiers are NOT called on Collection type itself; but are called for contained types.
     */
    fun constructCollectionType(collectionClass: KClass<out Collection<*>>, elementType: KotlinType): CollectionType {
        val bindings = TypeBindings.createIfNeeded(collectionClass, elementType)
        val result = fromClass(null, collectionClass, bindings) as CollectionType

        if (bindings.isEmpty()) {
            val realElementType = result.findSuperType(Collection::class)!!.contentType!!

            if (realElementType != elementType) {
                throw IllegalArgumentException(
                        "Non-generic Collection class ${collectionClass.qualifiedName} did not resolve to something with element type $elementType but $realElementType ")
            }
        }

        return result
    }

    /**
     * Method for constructing a [CollectionLikeType].
     *
     * NOTE: type modifiers are NOT called on constructed type itself; but are called for contained types.
     */
    fun constructCollectionLikeType(collectionClass: KClass<*>, elementClass: KClass<*>): CollectionLikeType {
        return constructCollectionLikeType(collectionClass, fromClass(null, elementClass, EMPTY_BINDINGS))
    }

    /**
     * Method for constructing a [CollectionLikeType].
     *
     * NOTE: type modifiers are NOT called on constructed type itself; but are called for contained types.
     */
    fun constructCollectionLikeType(collectionClass: KClass<*>, elementType: KotlinType): CollectionLikeType {
        val type = fromClass(null, collectionClass, TypeBindings.createIfNeeded(collectionClass, elementType))
        return type as? CollectionLikeType ?: CollectionLikeType.upgradeFrom(type, elementType)
    }

    /**
     * Method for constructing a [MapType] instance
     *
     * NOTE: type modifiers are NOT called on constructed type itself; but are called for contained types.
     */
    fun constructMapType(mapClass: KClass<out Map<*, *>>, keyClass: KClass<*>, valueClass: KClass<*>): MapType {
        val (keyType, valueType) = if (mapClass == Properties::class) {
            CORE_TYPE_STRING to CORE_TYPE_STRING
        } else {
            fromClass(null, keyClass, EMPTY_BINDINGS) to fromClass(null, valueClass, EMPTY_BINDINGS)
        }

        return constructMapType(mapClass, keyType, valueType)
    }

    /**
     * Method for constructing a [MapType] instance
     *
     * NOTE: type modifiers are NOT called on constructed type itself.
     */
    fun constructMapType(mapClass: KClass<out Map<*, *>>, keyType: KotlinType, valueType: KotlinType): MapType {
        val bindings = TypeBindings.createIfNeeded(mapClass, arrayOf(keyType, valueType))
        val result = fromClass(null, mapClass, bindings) as MapType

        if (!bindings.isEmpty()) {
            return result
        }

        val type = result.findSuperType(Map::class)!!
        val realKeyType = type.keyType!!

        if (realKeyType != keyType) {
            throw IllegalArgumentException(
                    "Non-generic Map class ${mapClass.qualifiedName} did not resolve to something with key type $keyType but $realKeyType ")
        }

        val realValueType = type.contentType!!

        if (realValueType != valueType) {
            throw IllegalArgumentException(
                    "Non-generic Map class ${mapClass.qualifiedName} did not resolve to something with value type $valueType but $realValueType ")
        }

        return result
    }

    /**
     * Method for constructing a [MapLikeType] instance.
     *
     * Do not use this method to create a true Map type -- use [constructMapType] instead. Map-like types are only meant
     * for supporting things that do not implement Map interface and as such cannot use standard Map handlers.
     *
     * NOTE: type modifiers are NOT called on constructed type itself; but are called for contained types.
     */
    fun constructMapLikeType(mapClass: KClass<*>, keyClass: KClass<*>, valueClass: KClass<*>): MapLikeType {
        return constructMapLikeType(mapClass, fromClass(null, keyClass, EMPTY_BINDINGS),
                fromClass(null, valueClass, EMPTY_BINDINGS))
    }

    /**
     * Method for constructing a [MapLikeType] instance.
     *
     * Do not use this method to create a true Map type -- use [constructMapType] instead. Map-like types are only meant
     * for supporting things that do not implement Map interface and as such cannot use standard Map handlers.
     *
     * NOTE: type modifiers are NOT called on constructed type itself.
     */
    fun constructMapLikeType(mapClass: KClass<*>, keyType: KotlinType, valueType: KotlinType): MapLikeType {
        val type = fromClass(null, mapClass, TypeBindings.createIfNeeded(mapClass, arrayOf(keyType, valueType)))
        return type as? MapLikeType ?: MapLikeType.upgradeFrom(type, keyType, valueType)
    }

    /**
     * Method for constructing a type instance with specified parameterization.
     *
     * NOTE: type modifiers are NOT called on constructed type itself.
     */
    fun constructSimpleType(rawType: KClass<*>, parameterTypes: Array<KotlinType?>): KotlinType {
        return fromClass(null, rawType, TypeBindings.create(rawType, parameterTypes))
    }

    /**
     * Method for constructing a [ReferenceType] instance with given type parameter (type MUST take one and only one
     * type parameter)
     *
     * NOTE: type modifiers are NOT called on constructed type itself.
     */
    fun constructReferenceType(rawType: KClass<*>, referredType: KotlinType): KotlinType {
        return ReferenceType.construct(rawType, TypeBindings.create(rawType, referredType), null, null, referredType)
    }

    /**
     * Factory method for constructing [KotlinType] that represents a parameterized type. For example, to represent type
     * `List<Set<Int>>`, you could call
     * ```
     * val inner = TypeFactory.constructParametricType(Set::class, Integer::class)
     * return TypeFactory.constructParametricType(List::class, inner)
     * ```
     *
     * @param rawType Type-erased type to parameterize
     *
     * @param parameterClasses Type parameters to apply
     */
    fun constructParametricType(rawType: KClass<*>, vararg parameterClasses: KClass<*>): KotlinType {
        return constructParametricType(rawType,
                *Array(parameterClasses.size) { fromClass(null, parameterClasses[it], EMPTY_BINDINGS) })
    }

    /**
     * Factory method for constructing [KotlinType] that represents a parameterized type. For example, to represent type
     * `List<Set<Int>>`, you could call
     * ```
     * val inner = TypeFactory.constructParametricType(Set::class, intKotlinType)
     * return TypeFactory.constructParametricType(List::class, inner)
     * ```
     *
     * @param rawType Actual type-erased type
     *
     * @param parameterTypes Type parameters to apply
     *
     * @return Fully resolved type for given base type and type parameters
     */
    fun constructParametricType(rawType: KClass<*>, vararg parameterTypes: KotlinType): KotlinType {
        return constructParametricType(rawType,
                TypeBindings.create(rawType, Array(parameterTypes.size) { parameterTypes[it] }))
    }

    /**
     * Factory method for constructing [KotlinType] that represents a parameterized type. The type's parameters are
     * specified as an instance of [TypeBindings]. This is useful if you already have the type's parameters such as
     * those found on [KotlinType]. For example, you could call
     * ```
     * return TypeFactory.constructParametricType(ArrayList::class, kotlinType.bindings)
     * ```
     * This effectively applies the parameterized types from one [KotlinType] to another class.
     *
     * @param rawType Actual type-erased type
     *
     * @param parameterTypes Type bindings for the raw type
     */
    fun constructParametricType(rawType: KClass<*>, parameterTypes: TypeBindings?): KotlinType {
        val resultType = fromClass(null, rawType, parameterTypes)
        return applyModifiers(rawType.java, resultType)
    }

    /*
     *******************************************************************************************************************
     * Direct factory methods for "raw" variants, used when parameterization is unknown
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to construct "raw" Collection type; meaning that its
     * parameterization is unknown.
     * This is similar to using `Any::class` parameterization,
     * and is equivalent to calling:
     * ```
     * typeFactory.constructCollectionType(collectionClass, TypeFactory.unknownType())
     * ```
     *
     * This method should only be used if parameterization is completely unavailable.
     */
    fun constructRawCollectionType(collectionClass: KClass<out Collection<*>>): CollectionType {
        return constructCollectionType(collectionClass, TypeFactory.unknownType())
    }

    /**
     * Method that can be used to construct "raw" Collection-like type; meaning that its
     * parameterization is unknown.
     * This is similar to using `Any::class` parameterization,
     * and is equivalent to calling:
     * ```
     * typeFactory.constructCollectionLikeType(collectionClass, TypeFactory.unknownType())
     * ```
     *
     * This method should only be used if parameterization is completely unavailable.
     */
    fun constructRawCollectionLikeType(collectionClass: KClass<out Collection<*>>): CollectionLikeType {
        return constructCollectionLikeType(collectionClass, TypeFactory.unknownType())
    }

    /**
     * Method that can be used to construct "raw" Map type; meaning that its
     * parameterization is unknown.
     * This is similar to using `Any::class` parameterization,
     * and is equivalent to calling:
     * ```
     * typeFactory.constructMapType(collectionClass, TypeFactory.unknownType(), TypeFactory.unknownType())
     * ```
     *
     * This method should only be used if parameterization is completely unavailable.
     */
    fun constructRawMapType(mapClass: KClass<out Map<*, *>>): MapType {
        return constructMapType(mapClass, TypeFactory.unknownType(), TypeFactory.unknownType())
    }

    /**
     * Method that can be used to construct "raw" Map-like type; meaning that its
     * parameterization is unknown.
     * This is similar to using `Any::class` parameterization,
     * and is equivalent to calling:
     * ```
     * typeFactory.constructMapLikeType(collectionClass, TypeFactory.unknownType(), TypeFactory.unknownType())
     * ```
     *
     * This method should only be used if parameterization is completely unavailable.
     */
    fun constructRawMapLikeType(mapClass: KClass<out Map<*, *>>): MapLikeType {
        return constructMapLikeType(mapClass, TypeFactory.unknownType(), TypeFactory.unknownType())
    }

    /*
     *******************************************************************************************************************
     * Low-level factory methods
     *******************************************************************************************************************
     */

    private fun mapType(rawClass: KClass<*>, bindings: TypeBindings, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        val (keyType, valueType) = if (rawClass == Properties::class) {
            CORE_TYPE_STRING to CORE_TYPE_STRING
        } else {
            val typeParameters = bindings.typeParameters

            when (val count = typeParameters.size) {
                0 -> unknownType() to unknownType()
                2 -> typeParameters[0] to typeParameters[1]
                else -> throw IllegalArgumentException(
                        "Strange Map type ${rawClass.qualifiedName} with $count type parameter${"s".takeIf { count > 1 } ?: ""} ($bindings), can not resolve")
            }
        }

        return MapType.construct(rawClass, bindings, superClass, superInterfaces, keyType, valueType)
    }

    private fun collectionType(rawClass: KClass<*>, bindings: TypeBindings, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        val typeParameters = bindings.typeParameters
        val contentType = if (typeParameters.isEmpty()) {
            unknownType()
        } else if (typeParameters.size == 1) {
            typeParameters[0]
        } else {
            throw IllegalArgumentException(
                    "Strange Collection type ${rawClass.qualifiedName}: cannot determine type parameters")
        }

        return CollectionType.construct(rawClass, bindings, superClass, superInterfaces, contentType)
    }

    private fun referenceType(rawClass: KClass<*>, bindings: TypeBindings, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        val typeParameters = bindings.typeParameters
        val contentType = if (typeParameters.isEmpty()) {
            unknownType()
        } else if (typeParameters.size == 1) {
            typeParameters[0]
        } else {
            throw IllegalArgumentException(
                    "Strange Reference type ${rawClass.qualifiedName}: cannot determine type parameters")
        }

        return ReferenceType.construct(rawClass, bindings, superClass, superInterfaces, contentType)
    }

    private fun iterationType(rawClass: KClass<*>, bindings: TypeBindings, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        val typeParameters = bindings.typeParameters
        val contentType = if (typeParameters.isEmpty()) {
            unknownType()
        } else if (typeParameters.size == 1) {
            typeParameters[0]
        } else {
            throw IllegalArgumentException(
                    "Strange Iteration type ${rawClass.qualifiedName}: cannot determine type parameters")
        }

        return iterationType(rawClass, bindings, superClass, superInterfaces, contentType)
    }

    private fun iterationType(rawClass: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?, iteratedType: KotlinType): KotlinType {
        return IterationType.construct(rawClass, bindings, superClass, superInterfaces, iteratedType)
    }

    /**
     * Factory method that is to create a new [SimpleType] with no checks whatsoever. Default implementation calls the
     * single argument constructor of [SimpleType].
     */
    private fun newSimpleType(rawClass: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        return SimpleType.construct(rawClass, bindings, superClass, superInterfaces)
    }

    private fun unknownType(): KotlinType {
        return CORE_TYPE_ANY
    }

    /**
     * Helper method called to see if requested, non-generic-parameterized type is one of common, "well-known" types,
     * instances of which are pre-constructed and do not need dynamic caching.
     */
    private fun findWellKnownSimple(clazz: KClass<*>): KotlinType? {
        return if (clazz.isPrimitive) {
            when (clazz) {
                CLASS_BOOLEAN -> CORE_TYPE_BOOLEAN
                CLASS_INT -> CORE_TYPE_INT
                CLASS_LONG -> CORE_TYPE_LONG
                CLASS_DOUBLE -> CORE_TYPE_DOUBLE
                else -> null
            }
        } else {
            when (clazz) {
                CLASS_STRING -> CORE_TYPE_STRING
                CLASS_ANY -> CORE_TYPE_ANY
                CLASS_CIRJSON_NODE -> CORE_TYPE_CIRJSON_NODE
                else -> null
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Actual type resolution, traversal
     *******************************************************************************************************************
     */

    /**
     * Factory method that can be used if type information is passed as typing returned from `genericXxx` accessors
     * (usually for a return or argument type).
     */
    private fun fromAny(context: ClassStack?, sourceType: Type, bindings: TypeBindings?): KotlinType {
        val resultType = when (sourceType) {
            is Class<*> -> fromClass(context, sourceType.kotlin, EMPTY_BINDINGS)
            is ParameterizedType -> fromParameterizedType(context, sourceType, bindings)
            is KotlinType -> return sourceType
            is GenericArrayType -> fromArrayType(context, sourceType, bindings)
            is TypeVariable<*> -> fromVariable(context, sourceType, bindings!!)
            is WildcardType -> fromWildcard(context, sourceType, bindings)
            else -> throw IllegalArgumentException("Unrecognized Type: $sourceType")
        }

        return applyModifiers(sourceType, resultType)
    }

    private fun applyModifiers(sourceType: Type, resolvedType: KotlinType): KotlinType {
        myModifiers ?: return resolvedType
        var resultType = resolvedType
        val bindings = resultType.bindings

        for (modifier in myModifiers) {
            resultType = modifier.modifyType(resultType, sourceType, bindings, this)
        }

        return resultType
    }

    /**
     * @param bindings Mapping of formal parameter declarations (for generic types) into actual types
     */
    internal fun fromClass(context: ClassStack?, rawType: KClass<*>, bindings: TypeBindings?): KotlinType {
        var result = findWellKnownSimple(rawType)

        if (result != null) {
            return result
        }

        val key = if (bindings == null || bindings.isEmpty()) {
            rawType
        } else {
            bindings.asKey(rawType)
        }

        result = key?.let { myTypeCache[it] }

        if (result != null) {
            return result
        }

        val realContext = if (context == null) {
            ClassStack(rawType)
        } else {
            val previous = context.find(rawType)

            if (previous != null) {
                val selfReference = ResolvedRecursiveType(rawType, EMPTY_BINDINGS)
                previous.addSelfReference(selfReference)
                return selfReference
            }

            context.child(rawType)
        }

        if (rawType.isArray) {
            result = ArrayType.construct(fromAny(realContext, rawType.componentType.java, bindings), bindings)
            realContext.resolveSelfReferences(result)

            if (key != null && !result.hasHandlers()) {
                myTypeCache.setIfAbsent(key, result)
            }

            return result
        }

        val superClass = if (rawType.isInterface) {
            null
        } else {
            resolveSuperClass(realContext, rawType, bindings)
        }

        val superInterfaces = resolveSuperInterfaces(realContext, rawType, bindings)

        if (rawType == Properties::class) {
            result = MapType.construct(rawType, bindings, superClass, superInterfaces, CORE_TYPE_STRING,
                    CORE_TYPE_STRING)
        } else if (superClass != null) {
            result = superClass.refine(rawType, bindings, superClass, superInterfaces)
        }

        if (result != null) {
            realContext.resolveSelfReferences(result)

            if (key != null && !result.hasHandlers()) {
                myTypeCache.setIfAbsent(key, result)
            }

            return result
        }

        result = fromWellKnownClass(rawType, bindings, superClass, superInterfaces) ?: fromWellKnownInterface(rawType,
                bindings, superClass, superInterfaces) ?: newSimpleType(rawType, bindings, superClass, superInterfaces)

        realContext.resolveSelfReferences(result)

        if (key != null && !result.hasHandlers()) {
            myTypeCache.setIfAbsent(key, result)
        }

        return result
    }

    private fun resolveSuperClass(context: ClassStack?, rawType: KClass<*>,
            parentBindings: TypeBindings?): KotlinType? {
        val parent = rawType.java.genericSuperclass ?: return null
        return fromAny(context, parent, parentBindings)
    }

    private fun resolveSuperInterfaces(context: ClassStack?, rawType: KClass<*>,
            parentBindings: TypeBindings?): Array<KotlinType> {
        val types = rawType.java.genericInterfaces

        if (types.isEmpty()) {
            return NO_TYPES
        }

        return Array(types.size) { fromAny(context, types[it], parentBindings) }
    }

    /**
     * Helper class used to check whether exact class for which type is being constructed is one of well-known base
     * interfaces or classes that indicates alternate [KotlinType] implementation.
     */
    private fun fromWellKnownClass(rawType: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType? {
        val realBindings = bindings ?: EMPTY_BINDINGS

        if (rawType == Map::class) {
            return mapType(rawType, realBindings, superClass, superInterfaces)
        }

        if (rawType == Collection::class) {
            return collectionType(rawType, realBindings, superClass, superInterfaces)
        }

        if (rawType == AtomicReference::class || rawType == Optional::class) {
            return referenceType(rawType, realBindings, superClass, superInterfaces)
        }

        if (rawType == Iterator::class || rawType == Stream::class) {
            return iterationType(rawType, realBindings, superClass, superInterfaces)
        }

        if (BaseStream::class.isAssignableFrom(rawType)) {
            if (DoubleStream::class.isAssignableFrom(rawType)) {
                return iterationType(rawType, realBindings, superClass, superInterfaces, CORE_TYPE_DOUBLE)
            } else if (IntStream::class.isAssignableFrom(rawType)) {
                return iterationType(rawType, realBindings, superClass, superInterfaces, CORE_TYPE_INT)
            } else if (LongStream::class.isAssignableFrom(rawType)) {
                return iterationType(rawType, realBindings, superClass, superInterfaces, CORE_TYPE_LONG)
            }
        }

        val referenced = when (rawType) {
            OptionalInt::class -> CORE_TYPE_INT
            OptionalLong::class -> CORE_TYPE_LONG
            OptionalDouble::class -> CORE_TYPE_DOUBLE
            else -> return null
        }

        val base = newSimpleType(rawType, realBindings, superClass, superInterfaces)
        return ReferenceType.upgradeFrom(base, referenced)
    }

    private fun fromWellKnownInterface(rawType: KClass<*>, bindings: TypeBindings?, superClass: KotlinType?,
            superInterfaces: Array<KotlinType>): KotlinType? {
        for (superInterface in superInterfaces) {
            val result = superInterface.refine(rawType, bindings, superClass, superInterfaces)

            if (result != null) {
                return result
            }
        }

        return null
    }

    /**
     * This method deals with parameterized types, that is, first class generic classes.
     */
    private fun fromParameterizedType(context: ClassStack?, parameterizedType: ParameterizedType,
            parentBindings: TypeBindings?): KotlinType {
        val rawType = (parameterizedType.rawType as Class<*>).kotlin

        if (rawType == CLASS_ENUM) {
            return CORE_TYPE_ENUM
        }

        if (rawType == CLASS_COMPARABLE) {
            return CORE_TYPE_COMPARABLE
        }

        val arguments = parameterizedType.actualTypeArguments
        val newBindings = if (arguments.isEmpty()) {
            EMPTY_BINDINGS
        } else {
            TypeBindings.create(rawType, Array(arguments.size) { fromAny(context, arguments[it], parentBindings) })
        }

        return fromClass(context, rawType, newBindings)
    }

    private fun fromArrayType(context: ClassStack?, type: GenericArrayType, bindings: TypeBindings?): KotlinType {
        val elementType = fromAny(context, type.genericComponentType, bindings)
        return ArrayType.construct(elementType, bindings)
    }

    private fun fromVariable(context: ClassStack?, variable: TypeVariable<*>,
            bindings: TypeBindings): KotlinType {
        val name = variable.name
        val type = bindings.findBoundType(name)

        if (type != null) {
            return type
        }

        if (bindings.hasUnbound(name)) {
            return CORE_TYPE_ANY
        }

        val realBindings = bindings.withUnboundVariable(name)

        lateinit var bounds: Array<Type>

        synchronized(variable) {
            bounds = variable.bounds
        }

        return fromAny(context, bounds[0], realBindings)
    }

    private fun fromWildcard(context: ClassStack?, type: WildcardType, bindings: TypeBindings?): KotlinType {
        return fromAny(context, type.upperBounds[0], bindings)
    }

    companion object {

        /**
         * Default size used to construct [myTypeCache].
         */
        const val DEFAULT_MAX_CACHE_SIZE = 200

        /**
         * Globally shared instance, which has no custom configuration. Used by `ObjectMapper` to get the default
         * factory when constructed.
         */
        val DEFAULT_INSTANCE = TypeFactory()

        private val NO_TYPES = emptyArray<KotlinType>()

        private val EMPTY_BINDINGS = TypeBindings.EMPTY

        /*
         ***************************************************************************************************************
         * Constants for "well-known" classes
         ***************************************************************************************************************
         */

        private val CLASS_STRING = String::class

        private val CLASS_ANY = Any::class

        private val CLASS_COMPARABLE = Comparable::class

        private val CLASS_ENUM = Enum::class

        private val CLASS_CIRJSON_NODE = CirJsonNode::class

        private val CLASS_BOOLEAN = Boolean::class

        private val CLASS_DOUBLE = Double::class

        private val CLASS_INT = Int::class

        private val CLASS_LONG = Long::class

        /*
         ***************************************************************************************************************
         * Cached pre-constructed KotlinType instances
         ***************************************************************************************************************
         */

        private val CORE_TYPE_BOOLEAN = SimpleType.construct(CLASS_BOOLEAN)

        private val CORE_TYPE_DOUBLE = SimpleType.construct(CLASS_DOUBLE)

        private val CORE_TYPE_INT = SimpleType.construct(CLASS_INT)

        private val CORE_TYPE_LONG = SimpleType.construct(CLASS_LONG)

        private val CORE_TYPE_STRING = SimpleType.construct(CLASS_STRING)

        private val CORE_TYPE_ANY = SimpleType.construct(CLASS_ANY)

        /**
         * Cache [Comparable] because it is both parametric (relatively costly to resolve) and mostly useless (no
         * special handling), better handle directly
         */
        private val CORE_TYPE_COMPARABLE = SimpleType.construct(CLASS_COMPARABLE)

        /**
         * Cache [Enum] because it is parametric AND self-referential (costly to resolve) and useless in itself (no
         * special handling).
         */
        private val CORE_TYPE_ENUM = SimpleType.construct(CLASS_ENUM)

        /**
         * Cache [CirJsonNode] because it is no critical path of simple tree model reading and does not have things to
         * override
         */
        private val CORE_TYPE_CIRJSON_NODE = SimpleType.construct(CLASS_CIRJSON_NODE)

        /*
         ***************************************************************************************************************
         * Static methods for non-instance-specific functionality
         ***************************************************************************************************************
         */

        /**
         * Method for constructing a marker type that indicates missing generic type information, which is handled same
         * as simple type for `Any`.
         */
        fun unknownType(): KotlinType {
            return DEFAULT_INSTANCE.unknownType()
        }

    }

}