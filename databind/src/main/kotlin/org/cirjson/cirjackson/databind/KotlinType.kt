package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.core.type.ResolvedType
import org.cirjson.cirjackson.databind.type.TypeBindings
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.*
import java.lang.reflect.Type
import kotlin.reflect.KClass

/**
 * Base class for type token classes used both to contain information and as keys for deserializers.
 *
 * Instances can (only) be constructed by [TypeFactory].
 */
@Suppress("EqualsOrHashCode")
abstract class KotlinType : ResolvedType, Type {

    /**
     * This is the nominal type-erased Class that would be close to the type represented (but not exactly type, due to
     * type erasure: type instance may have more information on this). It may be an interface or abstract class, so
     * instantiation may not be possible.
     */
    protected val myClass: KClass<*>

    protected val myHash: Int

    /**
     * Optional handler (codec) that can be attached to indicate what to use for handling (serializing, deserializing)
     * values of this specific type.
     *
     * Note: untyped (i.e. caller has to cast) because it is used for different kinds of handlers, with unrelated types.
     */
    protected val myValueHandler: Any?

    /**
     * Optional handler that can be attached to indicate how to handle additional type metadata associated with this
     * type.
     *
     * Note: untyped (i.e. caller has to cast) because it is used for different kinds of handlers, with unrelated types.
     */
    protected val myTypeHandler: Any?

    /**
     * Whether entities defined with this type should be handled using static typing (as opposed to dynamic runtime
     * type) or not.
     *
     * Note that while value of `true` does mean that static typing is to be used, value of `false` may still be
     * overridden by other settings.
     */
    val isUsedAsStaticType: Boolean

    /**
     * Main base constructor for subclasses to use
     *
     * @param raw "Raw" (type-erased) class for this type
     *
     * @param additionalHash Additional hash code to use, in addition to hash code of the class name
     *
     * @param valueHandler internal handler (serializer/deserializer) to apply for this type
     *
     * @param typeHandler internal type handler (type serializer/deserializer) to apply for this type
     *
     * @param isUsedAsStaticType Whether this type declaration will force specific type as opposed to being a base type
     * (usually for serialization typing)
     */
    protected constructor(raw: KClass<*>, additionalHash: Int, valueHandler: Any?, typeHandler: Any?,
            isUsedAsStaticType: Boolean) {
        myClass = raw
        myHash = 31 * additionalHash + raw.hashCode()
        myValueHandler = valueHandler
        myTypeHandler = typeHandler
        this.isUsedAsStaticType = isUsedAsStaticType
    }

    /**
     * Copy-constructor used when refining/upgrading type instances.
     *
     * @param base The type to copy
     */
    protected constructor(base: KotlinType) {
        myClass = base.myClass
        myHash = base.myHash
        myValueHandler = base.myValueHandler
        myTypeHandler = base.myTypeHandler
        isUsedAsStaticType = base.isUsedAsStaticType
    }

    /**
     * Mutant factory method that may be called on structured types that have a so-called content type (element of
     * arrays, value type of Maps, referenced type of referential types), and will construct a new instance that is
     * identical to this instance, except that it has specified content type, instead of current one. If content type is
     * already set to given type, `this` is returned. If type does not have a content type (which is the case with
     * `SimpleType`), [IllegalArgumentException] will be thrown.
     *
     * @param contentType The type of the content
     *
     * @return Newly created type instance
     */
    abstract fun withContentType(contentType: KotlinType): KotlinType

    /**
     * Method that can be called to get a type instance that indicates that values of the type should be handled using
     * "static typing" for purposes of serialization (as opposed to "dynamic" aka runtime typing): meaning that no
     * runtime information is needed for determining serializers to use. The main use case is to allow forcing of
     * specific root value serialization type, and specifically in resolving serializers for contained types (element
     * types for arrays, Collections and Maps).
     */
    abstract fun withStaticTyping(): KotlinType

    /*
     *******************************************************************************************************************
     * Internal factory methods for CirJackson-databind (not for users)
     *******************************************************************************************************************
     */

    /**
     * This mutant factory method will construct a new instance that is identical to this instance, except that it will
     * have specified type handler assigned.
     *
     * @param handler Handler to pass to new instance created
     *
     * @return Newly created type instance with same type information, specified handler
     */
    internal abstract fun withTypeHandler(handler: Any?): KotlinType

    /**
     * This mutant factory method will construct a new instance that is identical to this instance, except that it will
     * have specified content type (element type for arrays, value type for Maps and so forth) handler assigned.
     *
     * @param handler Handler to pass to new instance created
     *
     * @return Newly created type instance with same type information, specified handler
     */
    internal abstract fun withContentTypeHandler(handler: Any?): KotlinType

    /**
     * This mutant factory method will construct a new instance that is identical to this instance, except that it will
     * have specified value handler assigned.
     *
     * @param handler Handler to pass to new instance created
     *
     * @return Newly created type instance with same type information, specified handler
     */
    internal abstract fun withValueHandler(handler: Any?): KotlinType

    /**
     * Mutant factory method that will construct a new instance that is identical to this instance, except that it will
     * have specified content value handler assigned.
     *
     * @param handler Handler to pass to new instance created
     *
     * @return Newly created type instance with same type information, specified handler
     */
    internal abstract fun withContentValueHandler(handler: Any?): KotlinType

    /**
     * Mutant factory method that will try to copy handlers that the specified source type instance had, if any; this
     * must be done recursively where necessary (as content types may be structured).
     *
     * @param src The type to copy from
     *
     * @return Newly created type instance, or `this`
     */
    internal fun withHandlersFrom(src: KotlinType): KotlinType {
        var type = this

        var handler = src.typeHandler

        if (handler !== myTypeHandler) {
            type = type.withTypeHandler(handler)
        }

        handler = src.valueHandler

        if (handler !== myValueHandler) {
            type = type.withValueHandler(handler)
        }

        return type
    }

    /*
     *******************************************************************************************************************
     * Type coercion fluent factory methods
     *******************************************************************************************************************
     */

    /**
     * Mutant factory method that will try to create and return a subtype instance for known parameterized types; for
     * other types will return `null` to indicate that no just refinement makes necessary sense, without trying to
     * detect special status through implemented interfaces.
     */
    abstract fun refine(raw: KClass<*>, bindings: TypeBindings, superClass: KotlinType,
            superInterfaces: Array<KotlinType>): KotlinType?

    /*
     *******************************************************************************************************************
     * Implementation of ResolvedType API
     *******************************************************************************************************************
     */

    override val rawClass: KClass<*>
        get() = myClass

    /**
     * Method that can be used to check whether this type has specified KClass as its type erasure. Put another way,
     * returns `true` if instantiation of this Type is given (type-erased) Class.
     */
    override fun hasRawClass(clazz: KClass<*>): Boolean {
        return myClass == clazz
    }

    /**
     * Accessor that allows determining whether [contentType] should return a non-`null` value (that is, there is
     * a "content type") or not.
     *
     * @return `true` if [isContainerType] or [isReferenceType] return true.
     */
    open fun hasContentType(): Boolean {
        return true
    }

    fun isTypeOrSubTypeOf(clazz: KClass<*>): Boolean {
        return myClass == clazz || clazz.isAssignableFrom(myClass)
    }

    fun isTypeOrSuperTypeOf(clazz: KClass<*>): Boolean {
        return myClass == clazz || myClass.isAssignableFrom(clazz)
    }

    override val isAbstract: Boolean
        get() = myClass.isAbstract

    /**
     * Convenience accessor for checking whether underlying Java type is a concrete class or not: abstract classes and
     * interfaces are not.
     */
    override val isConcrete: Boolean
        get() {
            return !myClass.isInterface && !myClass.isAbstract || myClass.isPrimitive
        }

    override val isThrowable: Boolean
        get() = Throwable::class.isAssignableFrom(myClass)

    override val isArrayType: Boolean
        get() = false

    /**
     * Accessor that basically does the equivalent of:
     * ```
     * Enum::class.java.isAssignableFrom(rawClass)
     * ```
     * that is, return `true` if the underlying type erased class is `Enum` or one its subtypes (Enum implementations).
     */
    final override val isEnumType: Boolean
        get() = myClass.isEnumType

    /**
     * Similar to [isEnumType] except does NOT return `true` for [Enum] (since that is not Enum implementation type).
     */
    val isEnumImplType: Boolean
        get() = myClass.isEnumType && myClass != Enum::class.java

    val isRecordType: Boolean
        get() = myClass.isRecordType

    /**
     * Accessor that returns `true` if this instance is of type `IterationType`.
     */
    open val isIterationType: Boolean
        get() = false

    final override val isInterface: Boolean
        get() = myClass.isInterface

    final override val isPrimitive: Boolean
        get() = myClass.isPrimitive

    final override val isFinal: Boolean
        get() = myClass.isFinal

    /**
     * Accessor that returns `true` if type represented is a container type; this includes array, Map and Collection
     * types.
     */
    abstract override val isContainerType: Boolean

    /**
     * Accessor that returns `true` if type is either true [Collection] type, or something similar (meaning it has at
     * least one type parameter, which describes type of contents)
     */
    override val isCollectionLikeType: Boolean
        get() = false

    /**
     * Convenience accessor, shorthand for `rawClass == Object::class.java` and used to figure if we basically have
     * "untyped" type object.
     */
    val isJavaLangObject: Boolean
        get() = myClass == Object::class.java

    override val hasGenericTypes: Boolean
        get() = containedTypeCount() > 0

    override val keyType: KotlinType?
        get() = null

    override val contentType: KotlinType?
        get() = null

    override val referencedType: KotlinType?
        get() = null

    abstract override fun containedType(index: Int): KotlinType?

    /*
     *******************************************************************************************************************
     * Extended API beyond ResolvedType
     *******************************************************************************************************************
     */

    /**
     * Convenience method that is shorthand for `containedType(index) ?: TypeFactory.unknownType()` and typically used
     * to eliminate need for `null` checks for common case where we just want to check if containedType is available
     * first; and if not, use "unknown type" (which translates to `Any` basically).
     */
    fun containedTypeOrUnknown(index: Int): KotlinType {
        return containedType(index) ?: TypeFactory.unknownType()
    }

    abstract val bindings: TypeBindings

    /**
     * Method that may be called to find representation of given type within type hierarchy of this type: either this
     * type (if this type has given erased type), one of its supertypes that has the erased types, or `null` if target
     * is neither this type nor any of its supertypes.
     */
    abstract fun findSuperType(erasedTarget: Class<*>): KotlinType?

    /**
     * Accessor for finding fully resolved parent class of this type, if it has one; `null` if not.
     */
    abstract val superClass: KotlinType?

    /**
     * Accessor for finding fully resolved interfaces this type implements, if any; empty array if none.
     */
    abstract val interfaces: List<KotlinType>

    /**
     * Method that may be used to find parameterization this type has for given type-erased generic target type.
     */
    abstract fun findTypeParameters(expectedType: Class<*>): Array<KotlinType>

    /*
     *******************************************************************************************************************
     * Internal accessors API, accessing handlers
     *******************************************************************************************************************
     */

    /**
     * Type handler associated with this type, if any.
     */
    internal val typeHandler: Any?
        get() = myTypeHandler

    /**
     * Content type handler associated with this type, if any.
     */
    internal open val contentTypeHandler: Any?
        get() = null

    /**
     * Value handler associated with this type, if any.
     */
    internal val valueHandler: Any?
        get() = myTypeHandler

    /**
     * Content value handler associated with this type, if any.
     */
    internal open val contentValueHandler: Any?
        get() = null

    internal fun hasValueHandler() = myValueHandler != null

    /**
     * Helper method that checks whether this type, or its (optional) key or content type has [valueHandler] or
     * [typeHandler]; that is, are there any non-standard handlers associated with this type object.
     */
    internal open fun hasHandlers() = myTypeHandler != null || myValueHandler != null

    /*
     *******************************************************************************************************************
     * Support for producing signatures
     *******************************************************************************************************************
     */

    /**
     * Accessor for signature that contains generic type information, in form compatible with JVM 1.5 as per JLS. It is
     * a superset of [erasedSignature], in that generic information can be automatically removed if necessary (just
     * remove outermost angle brackets along with content inside)
     */
    val genericSignature: String
        get() {
            val stringBuilder = StringBuilder(40)
            getGenericSignature(stringBuilder)
            return stringBuilder.toString()
        }

    /**
     * Method for accessing signature that contains generic type information, in form compatible with JVM 1.5 as per
     * JLS. It is a superset of [getErasedSignature], in that generic information can be automatically removed if
     * necessary (just remove outermost angle brackets along with content inside)
     *
     * @param stringBuilder StringBuilder to append signature to
     *
     * @return StringBuilder that was passed in; returned to allow call chaining
     */
    abstract fun getGenericSignature(stringBuilder: StringBuilder): StringBuilder

    /**
     * Accessor for signature without generic type information, in form compatible with all versions of JVM, and
     * specifically used for type descriptions when generating byte code.
     */
    val erasedSignature: String
        get() {
            val stringBuilder = StringBuilder(40)
            getErasedSignature(stringBuilder)
            return stringBuilder.toString()
        }

    /**
     * Method for accessing signature without generic type information, in form compatible with all versions of JVM, and
     * specifically used for type descriptions when generating byte code.
     *
     * @param stringBuilder StringBuilder to append signature to
     *
     * @return StringBuilder that was passed in; returned to allow call chaining
     */
    abstract fun getErasedSignature(stringBuilder: StringBuilder): StringBuilder

    /*
     *******************************************************************************************************************
     * Standard methods (abstract to force override)
     *******************************************************************************************************************
     */

    abstract override fun toString(): String

    abstract override fun equals(other: Any?): Boolean

    override fun hashCode(): Int = myHash

}