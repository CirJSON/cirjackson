package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.type.TypeBindings
import org.cirjson.cirjackson.databind.util.Annotations
import org.cirjson.cirjackson.databind.util.hasClass
import org.cirjson.cirjackson.databind.util.isNonStaticInnerClass
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

    private var myCreators: Creators? = null

    /**
     * Member methods of interest; for now ones with 0 or 1 arguments (just optimization, since others won't be used
     * now)
     */
    private var myMemberMethods: AnnotatedMethodMap? = null

    /**
     * Member fields of interest: ones that are either public, or have at least one annotation.
     */
    private var myFields: List<AnnotatedField>? = null

    /**
     * Lazily determined property to see if this is a non-static inner class.
     */
    private var myNonStaticInnerClass: Boolean? = null

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

    fun hasAnnotations(): Boolean {
        return annotations.size > 0
    }

    val defaultConstructor: AnnotatedConstructor?
        get() = creators().defaultConstructor

    val constructors: List<AnnotatedConstructor>
        get() = creators().constructors

    val factoryMethods: List<AnnotatedMethod>
        get() = creators().creatorMethods

    fun memberMethods(): Iterable<AnnotatedMethod> {
        return methods()
    }

    val memberMethodCount: Int
        get() = methods().size

    fun findMethod(name: String, parameterTypes: Array<KClass<*>>): AnnotatedMethod? {
        return methods().find(name, parameterTypes)
    }

    fun fields(): Iterable<AnnotatedField> {
        return fieldsInternal()
    }

    val fieldCount: Int
        get() = fieldsInternal().size

    val isNonStaticInnerClass: Boolean
        get() = myNonStaticInnerClass ?: myClass.isNonStaticInnerClass.also { myNonStaticInnerClass = it }

    /*
     *******************************************************************************************************************
     * Lazily-operating accessors
     *******************************************************************************************************************
     */

    private fun fieldsInternal(): List<AnnotatedField> {
        var fields = myFields

        if (fields != null) {
            return fields
        }

        fields = if (myType == null) {
            emptyList()
        } else {
            AnnotatedFieldCollector.collectFields(myConfig, this, myMixInResolver, myType, myPrimaryMixIn,
                    myCollectAnnotations)
        }

        return fields.also { myFields = it }
    }

    private fun methods(): AnnotatedMethodMap {
        var methods = myMemberMethods

        if (methods != null) {
            return methods
        }

        methods = if (myType == null) {
            AnnotatedMethodMap()
        } else {
            AnnotatedMethodCollector.collectMethods(myConfig, this, myMixInResolver, myType, mySupertypes,
                    myPrimaryMixIn, myCollectAnnotations)
        }

        return methods.also { myMemberMethods = it }
    }

    private fun creators(): Creators {
        var creators = myCreators

        if (creators != null) {
            return creators
        }

        creators = if (myType == null) {
            NO_CREATORS
        } else {
            AnnotatedCreatorCollector.collectCreators(myConfig, this, myType, myPrimaryMixIn, myCollectAnnotations)
        }

        return creators.also { myCreators = it }
    }

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */

    override fun toString(): String {
        return "[AnnotatedClass ${myClass.qualifiedName}]"
    }

    override fun hashCode(): Int {
        return myClass.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other === this) {
            return true
        }

        if (other == null || !other.hasClass(this::class) || other !is AnnotatedClass) {
            return false
        }

        return myClass == other.myClass
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

    companion object {

        private val NO_CREATORS = Creators(null, emptyList(), emptyList())

    }

}