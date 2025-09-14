package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.type.TypeBindings
import org.cirjson.cirjackson.databind.util.Annotations
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

class AnnotatedClass : Annotated, TypeResolutionContext {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    private val myConfig: MapperConfig<*>?

    /**
     * Resolved Kotlin type for which information is collected: also works as context for resolving possible generic
     * type of accessors declared in this type.
     */
    private val myType: KotlinType?

    /**
     * Type erased [KClass] matching {@code myType}.
     */
    private val myClass: KClass<*>

    /**
     * Type bindings to use for members of [myClass].
     */
    private val myBindings: TypeBindings

    /**
     * Ordered set of super classes and interfaces of the class itself: included in order of precedence
     */
    private val mySupertypes: List<KotlinType>

    /**
     * Object that knows mapping of mix-in classes (ones that contain annotations to add) with their target classes
     * (ones that get these additional annotations "mixed in").
     */
    private val myMixInResolver: MixInResolver?

    /**
     * Primary mix-in class; one to use for the annotated class itself. Can be `null`.
     */
    private val myPrimaryMixIn: KClass<*>?

    /**
     * Flag that indicates whether (full) annotation resolution should occur: is disabled for JDK container types.
     */
    private val myCollectAnnotations: Boolean

    /*
     *******************************************************************************************************************
     * Gathered information
     *******************************************************************************************************************
     */

    /**
     * Combined list of Jackson annotations that the class has, including inheritable ones from super classes and
     * interfaces
     */
    val annotations: Annotations

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    /**
     * Constructor will not do any initializations, to allow for configuring instances differently depending on use cases
     *
     * @param type Fully resolved type; may be `null`, but ONLY if no member fields or methods are to be accessed
     *
     * @param rawType Type-erased class; pass if no `type` needed or available
     */
    internal constructor(config: MapperConfig<*>?, type: KotlinType?, rawType: KClass<*>, supertypes: List<KotlinType>,
            primaryMixIn: KClass<*>?, classAnnotations: Annotations, bindings: TypeBindings,
            mixInResolver: MixInResolver?, collectAnnotations: Boolean) : super() {
        myConfig = config
        myType = type
        myClass = rawType
        mySupertypes = supertypes
        myPrimaryMixIn = primaryMixIn
        annotations = classAnnotations
        myBindings = bindings
        myMixInResolver = mixInResolver
        myCollectAnnotations = collectAnnotations
    }

    /**
     * Constructor (only) used for creating primordial simple types (during bootstrapping) and array type placeholders
     * where no fields or methods are needed.
     */
    internal constructor(rawType: KClass<*>) : super() {
        myConfig = null
        myType = null
        myClass = rawType
        mySupertypes = emptyList()
        myPrimaryMixIn = null
        annotations = AnnotationCollector.EMPTY_ANNOTATIONS
        myBindings = TypeBindings.EMPTY
        myMixInResolver = null
        myCollectAnnotations = false
    }

    /*
     *******************************************************************************************************************
     * TypeResolutionContext implementation
     *******************************************************************************************************************
     */

    override fun resolveType(type: KType): KotlinType {
        return myConfig!!.typeFactory.resolveMemberType(type.javaType, myBindings)
    }

    /*
     *******************************************************************************************************************
     * Annotated implementation
     *******************************************************************************************************************
     */

    override val annotated: KClass<*>
        get() = myClass

    override val modifiers: Int
        get() = myClass.java.modifiers

    override val name: String
        get() = myClass.qualifiedName!!

    override fun <A : Annotation> getAnnotation(kClass: KClass<A>): A? {
        return annotations[kClass]
    }

    override fun hasAnnotation(kClass: KClass<*>): Boolean {
        return annotations.has(kClass)
    }

    override fun hasOneOf(annotationClasses: Array<KClass<out Annotation>>): Boolean {
        return annotations.hasOneOf(annotationClasses)
    }

    override val rawType: KClass<*>
        get() = myClass

    override val type: KotlinType
        get() = myType!!

    /*
     *******************************************************************************************************************
     * Public API, generic accessors
     *******************************************************************************************************************
     */

    /*
     *******************************************************************************************************************
     * Lazily-operating accessors
     *******************************************************************************************************************
     */

    private fun fields(): List<AnnotatedField> {
        TODO("Not yet implemented")
    }

    private fun methods(): AnnotatedMethodMap {
        TODO("Not yet implemented")
    }

    private fun creators(): Creators {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */

    override fun toString(): String {
        TODO("Not yet implemented")
    }

    override fun hashCode(): Int {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    /**
     * @property defaultConstructor Default constructor of the annotated class, if it has one.
     *
     * @property constructors Single argument constructors the class has, if any.
     *
     * @property creatorMethods Single argument static methods that might be usable as factory methods
     */
    class Creators(val defaultConstructor: AnnotatedConstructor?, val constructors: List<AnnotatedConstructor>,
            val creatorMethods: List<AnnotatedMethod>)

}