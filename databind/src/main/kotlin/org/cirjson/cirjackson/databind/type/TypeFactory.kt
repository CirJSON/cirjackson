package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.core.util.Snapshottable
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.util.*
import java.lang.reflect.GenericArrayType
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable
import java.lang.reflect.WildcardType
import java.util.EnumMap
import java.util.EnumSet
import java.util.LinkedList
import java.util.TreeMap
import java.util.TreeSet
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
                Array(typeParametersCount) { placeholders[it].actualType() ?: unknownType() })
    }

    @Throws(IllegalArgumentException::class)
    private fun resolveTypePlaceholders(sourceType: KotlinType, actualType: KotlinType): String? {
        val expectedTypes = sourceType.bindings.typeParameters
        val actualTypes = actualType.bindings.typeParameters

        for (i in expectedTypes.indices) {
            val expected = expectedTypes[i]
            val actual = actualTypes.getOrNull(i) ?: unknownType()

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
        return type.findSuperType(expectedType)?.bindings?.typeParameterArray() ?: NO_TYPES
    }

    /**
     * Specialized alternative to [findTypeParameters]
     */
    fun findFirstTypeParameters(type: KotlinType, expectedType: KClass<*>): KotlinType {
        return type.findSuperType(expectedType)?.bindings?.getBoundTypeOrNull(0) ?: unknownType()
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
        TODO("Not yet implemented")
    }

    fun constructType(typeReference: TypeReference<*>): KotlinType {
        TODO("Not yet implemented")
    }

    fun resolveMemberType(type: Type, contextBindings: TypeBindings): KotlinType {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Direct factory methods
     *******************************************************************************************************************
     */

    fun constructArrayType(elementType: KClass<*>): ArrayType {
        TODO("Not yet implemented")
    }

    fun constructArrayType(elementType: KotlinType): ArrayType {
        TODO("Not yet implemented")
    }

    fun constructCollectionType(collectionClass: KClass<out Collection<*>>, elementClass: KClass<*>): CollectionType {
        TODO("Not yet implemented")
    }

    fun constructCollectionType(collectionClass: KClass<out Collection<*>>, elementType: KotlinType): CollectionType {
        TODO("Not yet implemented")
    }

    fun constructCollectionLikeType(collectionClass: KClass<*>, elementClass: KClass<*>): CollectionLikeType {
        TODO("Not yet implemented")
    }

    fun constructCollectionLikeType(collectionClass: KClass<*>, elementType: KotlinType): CollectionLikeType {
        TODO("Not yet implemented")
    }

    fun constructMapType(mapClass: KClass<out Map<*, *>>, keyClass: KClass<*>, valueClass: KClass<*>): MapType {
        TODO("Not yet implemented")
    }

    fun constructMapType(mapClass: KClass<out Map<*, *>>, keyType: KotlinType, valueType: KotlinType): MapType {
        TODO("Not yet implemented")
    }

    fun constructMapLikeType(mapClass: KClass<*>, keyClass: KClass<*>, valueClass: KClass<*>): MapLikeType {
        TODO("Not yet implemented")
    }

    fun constructMapLikeType(mapClass: KClass<*>, keyType: KotlinType, valueType: KotlinType): MapLikeType {
        TODO("Not yet implemented")
    }

    fun constructSimpleType(rawType: KClass<*>, parameterTypes: Array<KotlinType>): KotlinType {
        TODO("Not yet implemented")
    }

    fun constructReferenceType(rawType: KClass<*>, referredType: KotlinType): KotlinType {
        TODO("Not yet implemented")
    }

    fun constructParametricType(rawType: KClass<*>, vararg parameterClasses: KClass<*>): KotlinType {
        TODO("Not yet implemented")
    }

    fun constructParametricType(rawType: KClass<*>, vararg parameterTypes: KotlinType): KotlinType {
        TODO("Not yet implemented")
    }

    fun constructParametricType(rawType: KClass<*>, parameterTypes: TypeBindings): KotlinType {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Direct factory methods for "raw" variants, used when parameterization is unknown
     *******************************************************************************************************************
     */

    fun constructRawCollectionType(collectionClass: KClass<out Collection<*>>): CollectionType {
        TODO("Not yet implemented")
    }

    fun constructRawCollectionLikeType(collectionClass: KClass<out Collection<*>>): CollectionLikeType {
        TODO("Not yet implemented")
    }

    fun constructRawMapType(mapClass: KClass<out Map<*, *>>): MapType {
        TODO("Not yet implemented")
    }

    fun constructRawMapLikeType(mapClass: KClass<out Map<*, *>>): MapLikeType {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Low-level factory methods
     *******************************************************************************************************************
     */

    private fun mapType(rawClass: KClass<*>, bindings: TypeBindings, superType: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        TODO("Not yet implemented")
    }

    private fun collectionType(rawClass: KClass<*>, bindings: TypeBindings, superType: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        TODO("Not yet implemented")
    }

    private fun referenceType(rawClass: KClass<*>, bindings: TypeBindings, superType: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        TODO("Not yet implemented")
    }

    private fun iterationType(rawClass: KClass<*>, bindings: TypeBindings, superType: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        TODO("Not yet implemented")
    }

    private fun iterationType(rawClass: KClass<*>, bindings: TypeBindings?, superType: KotlinType?,
            superInterfaces: Array<KotlinType>?, iteratedType: KotlinType): KotlinType {
        TODO("Not yet implemented")
    }

    private fun constructSimple(rawClass: KClass<*>, bindings: TypeBindings, superType: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        TODO("Not yet implemented")
    }

    private fun newSimpleType(rawClass: KClass<*>, bindings: TypeBindings?, superType: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType {
        TODO("Not yet implemented")
    }

    private fun unknownType(): KotlinType {
        TODO("Not yet implemented")
    }

    private fun findWellKnownSimple(clazz: KClass<*>): KotlinType? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Actual type resolution, traversal
     *******************************************************************************************************************
     */

    private fun fromAny(context: ClassStack?, sourceType: Type, bindings: TypeBindings?): KotlinType {
        TODO("Not yet implemented")
    }

    private fun applyModifiers(sourceType: Type, resolvedType: KotlinType): KotlinType {
        TODO("Not yet implemented")
    }

    internal fun fromClass(context: ClassStack?, rawType: KClass<*>, bindings: TypeBindings): KotlinType {
        TODO("Not yet implemented")
    }

    private fun resolveSuperClass(context: ClassStack?, rawType: KClass<*>, parentBindings: TypeBindings?): KotlinType {
        TODO("Not yet implemented")
    }

    private fun resolveSuperInterfaces(context: ClassStack?, rawType: KClass<*>,
            parentBindings: TypeBindings?): Array<KotlinType> {
        TODO("Not yet implemented")
    }

    private fun fromWellKnownClass(rawType: KClass<*>, bindings: TypeBindings?, superType: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType? {
        TODO("Not yet implemented")
    }

    private fun fromWellKnownInterface(rawType: KClass<*>, bindings: TypeBindings?, superType: KotlinType?,
            superInterfaces: Array<KotlinType>?): KotlinType? {
        TODO("Not yet implemented")
    }

    private fun fromParameterizedType(context: ClassStack?, parameterizedType: ParameterizedType,
            parentBindings: TypeBindings?): KotlinType {
        TODO("Not yet implemented")
    }

    private fun fromArrayType(context: ClassStack?, type: GenericArrayType, parentBindings: TypeBindings?): KotlinType {
        TODO("Not yet implemented")
    }

    private fun fromVariable(context: ClassStack?, parameterizedType: TypeVariable<*>,
            parentBindings: TypeBindings?): KotlinType {
        TODO("Not yet implemented")
    }

    private fun fromWildcard(context: ClassStack?, type: WildcardType, parentBindings: TypeBindings?): KotlinType {
        TODO("Not yet implemented")
    }

    companion object {

        /**
         * Default size used to construct [typeCache].
         */
        const val DEFAULT_MAX_CACHE_SIZE = 200

        /**
         * Globally shared instance, which has no custom configuration. Used by `ObjectMapper` to get the default
         * factory when constructed.
         */
        val DEFAULT_INSTANCE = TypeFactory()

        private val NO_TYPES = arrayOfNulls<KotlinType>(0)

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
            TODO("Not yet implemented")
        }

    }

}