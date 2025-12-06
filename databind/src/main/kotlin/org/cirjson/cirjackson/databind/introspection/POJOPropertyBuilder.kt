package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.annotations.CirJsonProperty
import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyMetadata
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import kotlin.reflect.KClass

/**
 * Helper class used for aggregating information about a single potential POJO property.
 */
open class POJOPropertyBuilder : BeanPropertyDefinition, Comparable<POJOPropertyBuilder> {

    /**
     * Whether property is being composed for serialization (`true`) or deserialization (`false`)
     */
    protected val myForSerialization: Boolean

    protected val myConfig: MapperConfig<*>

    protected val myAnnotationIntrospector: AnnotationIntrospector?

    /**
     * External name of logical property; may change with renaming (by new instance being constructed using a new name)
     */
    protected val myName: PropertyName

    /**
     * Original internal name, derived from accessor, of this property. Will not be changed by renaming.
     */
    protected val myInternalName: PropertyName

    protected var myFields: Linked<AnnotatedField>?

    protected var myConstructorParameters: Linked<AnnotatedField>?

    protected var myGetters: Linked<AnnotatedField>?

    protected var mySetters: Linked<AnnotatedField>?

    @Transient
    protected var myMetadata: PropertyMetadata? = null

    /**
     * Lazily accessed information about this property iff it is a forward or back reference.
     */
    @Transient
    protected var myReferenceInfo: AnnotationIntrospector.ReferenceProperty? = null

    constructor(config: MapperConfig<*>, annotationIntrospector: AnnotationIntrospector?, forSerialization: Boolean,
            internalName: PropertyName) : this(config, annotationIntrospector, forSerialization, internalName,
            internalName)

    protected constructor(config: MapperConfig<*>, annotationIntrospector: AnnotationIntrospector?,
            forSerialization: Boolean, internalName: PropertyName, name: PropertyName) : super() {
        myConfig = config
        myAnnotationIntrospector = annotationIntrospector
        myInternalName = internalName
        myName = name
        myForSerialization = forSerialization
        myFields = null
        myConstructorParameters = null
        myGetters = null
        mySetters = null
    }

    protected constructor(source: POJOPropertyBuilder, newName: PropertyName) : super() {
        myConfig = source.myConfig
        myAnnotationIntrospector = source.myAnnotationIntrospector
        myInternalName = source.myInternalName
        myName = newName
        myForSerialization = source.myForSerialization
        myFields = source.myFields
        myConstructorParameters = source.myConstructorParameters
        myGetters = source.myGetters
        mySetters = source.mySetters
    }

    /*
     *******************************************************************************************************************
     * Mutant factory methods
     *******************************************************************************************************************
     */

    override fun withName(newName: PropertyName): BeanPropertyDefinition {
        TODO("Not yet implemented")
    }

    override fun withSimpleName(newSimpleName: String?): BeanPropertyDefinition {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Comparable implementation
     *******************************************************************************************************************
     */

    override fun compareTo(other: POJOPropertyBuilder): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * BeanPropertyDefinition implementation, name/type
     *******************************************************************************************************************
     */

    override val name: String
        get() = TODO("Not yet implemented")

    override val fullName: PropertyName
        get() = TODO("Not yet implemented")

    override fun hasName(name: PropertyName): Boolean {
        return super.hasName(name)
    }

    override val internalName: String
        get() = TODO("Not yet implemented")

    override val wrapperName: PropertyName?
        get() = TODO("Not yet implemented")

    override val isExplicitlyIncluded: Boolean
        get() = TODO("Not yet implemented")

    override val isExplicitlyNamed: Boolean
        get() = super.isExplicitlyNamed

    /*
     *******************************************************************************************************************
     * BeanPropertyDefinition implementation, simple metadata
     *******************************************************************************************************************
     */

    override val metadata: PropertyMetadata
        get() = TODO("Not yet implemented")

    /**
     * Helper method that contains logic for accessing and merging all setter information that we needed, regarding
     * things like possible merging of property value, and handling of incoming nulls. Only called for deserialization
     * purposes.
     */
    protected open fun getSetterInfo(metadata: PropertyMetadata, primary: AnnotatedMember?): PropertyMetadata {
        TODO("Not yet implemented")
    }

    /**
     * Type determined from the primary member for the property being built, considering precedence according to whether
     * we are processing serialization or deserialization.
     */
    override val primaryType: KotlinType
        get() = TODO("Not yet implemented")

    override val rawPrimaryType: KClass<*>
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * BeanPropertyDefinition implementation, accessor access
     *******************************************************************************************************************
     */

    override fun hasGetter(): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasSetter(): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasField(): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasConstructorParameter(): Boolean {
        TODO("Not yet implemented")
    }

    override fun couldDeserialize(): Boolean {
        return super.couldDeserialize()
    }

    override fun couldSerialize(): Boolean {
        return super.couldSerialize()
    }

    override val getter: AnnotatedMethod?
        get() = TODO("Not yet implemented")

    /**
     * Variant of [getter] that does NOT trigger pruning of getter candidates.
     */
    protected open val getterUnchecked: AnnotatedMethod?
        get() = TODO("Not yet implemented")

    override val setter: AnnotatedMethod?
        get() = TODO("Not yet implemented")

    /**
     * Variant of [setter] that does NOT trigger pruning of setter candidates.
     */
    protected open val setterUnchecked: AnnotatedMethod?
        get() = TODO("Not yet implemented")

    protected open fun selectSetterFromMultiple(current: Linked<AnnotatedMethod>,
            next: Linked<AnnotatedMethod>): AnnotatedMethod {
        TODO("Not yet implemented")
    }

    protected open fun selectSetter(currentMethod: AnnotatedMethod, nextMethod: AnnotatedMethod): AnnotatedMethod {
        TODO("Not yet implemented")
    }

    override val field: AnnotatedField?
        get() = TODO("Not yet implemented")

    /**
     * Variant of [field] that does NOT trigger pruning of field candidates.
     */
    protected open val fieldUnchecked: AnnotatedField?
        get() = TODO("Not yet implemented")

    override val constructorParameter: AnnotatedParameter?
        get() = TODO("Not yet implemented")

    override val constructorParameters: Iterator<AnnotatedParameter>
        get() = super.constructorParameters

    override val primaryMember: AnnotatedMember?
        get() = TODO("Not yet implemented")

    protected open val primaryMemberUnchecked: AnnotatedMember?
        get() = TODO("Not yet implemented")

    protected open fun getterPriority(method: AnnotatedMethod): Int {
        TODO("Not yet implemented")
    }

    protected open fun setterPriority(method: AnnotatedMethod): Int {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Implementations of refinement accessors
     *******************************************************************************************************************
     */

    override fun findViews(): Array<KClass<*>>? {
        return super.findViews()
    }

    override fun findReferenceType(): AnnotationIntrospector.ReferenceProperty? {
        return super.findReferenceType()
    }

    override val isTypeId: Boolean
        get() = super.isTypeId

    override fun findObjectIdInfo(): ObjectIdInfo? {
        return super.findObjectIdInfo()
    }

    override fun findInclusion(): CirJsonInclude.Value {
        TODO("Not yet implemented")
    }

    override fun findAliases(): List<PropertyName> {
        TODO("Not yet implemented")
    }

    open fun findAccess(): CirJsonProperty.Access? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Data aggregation
     *******************************************************************************************************************
     */

    /*
     *******************************************************************************************************************
     * Modifications
     *******************************************************************************************************************
     */

    /*
     *******************************************************************************************************************
     * Accessors for aggregate information
     *******************************************************************************************************************
     */

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    /*
     *******************************************************************************************************************
     * Helper classes
     *******************************************************************************************************************
     */

    protected fun interface WithMember<T> {

        fun withMember(member: AnnotatedMember): T?

    }

    protected class MemberIterator<T : AnnotatedMember>(private var myNext: Linked<T>?) : Iterator<T> {

        override fun hasNext(): Boolean {
            return myNext != null
        }

        override fun next(): T {
            val next = myNext ?: throw NoSuchElementException()
            val result = next.value
            myNext = next.next
            return result
        }

    }

    /**
     * Node used for creating simple linked lists to efficiently store small sets of things.
     */
    protected class Linked<T : Any>(val value: T, val next: Linked<T>?, name: PropertyName?, explicitName: Boolean,
            val isVisible: Boolean, val isMarkedIgnored: Boolean) {

        val name = name?.takeUnless { it.isEmpty() }

        val isNameExplicit = if (explicitName) {
            if (this.name == null) {
                throw IllegalArgumentException("Cannot pass true for 'explicitName' if name is null/empty")
            }

            this.name.hasSimpleName()
        } else {
            false
        }

        fun withoutNext(): Linked<T> {
            next ?: return this
            return Linked(value, null, name, isNameExplicit, isVisible, isMarkedIgnored)
        }

        fun withNext(newNext: Linked<T>?): Linked<T> {
            if (newNext === next) {
                return this
            }

            return Linked(value, newNext, name, isNameExplicit, isVisible, isMarkedIgnored)
        }

        fun withoutIgnored(): Linked<T>? {
            if (isMarkedIgnored) {
                return next?.withoutIgnored()
            }

            next ?: return this

            val newNext = next.withoutIgnored()

            if (newNext === next) {
                return this
            }

            return withNext(newNext)
        }

        fun withoutNonVisible(): Linked<T>? {
            val newNext = next?.withoutNonVisible()
            return if (isVisible) withNext(newNext) else newNext
        }

        /**
         * Method called to append given node(s) at the end of this node chain.
         */
        protected fun append(appendable: Linked<T>): Linked<T> {
            return next?.let { withNext(it.append(appendable)) } ?: withNext(appendable)
        }

        fun trimByVisibility(): Linked<T> {
            next ?: return this

            val newNext = next.trimByVisibility()

            if (name != null) {
                return withNext(newNext.takeUnless { it.name == null })
            }

            if (newNext.name != null) {
                return newNext
            }

            if (isVisible == newNext.isVisible) {
                return withNext(newNext)
            }

            return if (isVisible) withNext(null) else newNext
        }

        override fun toString(): String {
            var message = "$value[visible=$isVisible,ignore=$isMarkedIgnored,explicitName=$isNameExplicit]"

            if (next != null) {
                message += ", $next"
            }

            return message
        }

    }

    companion object {

        /**
         * Marker value used to denote that no reference-property information found for this property
         */
        private val NOT_REFERENCE_PROPERTY = AnnotationIntrospector.ReferenceProperty.managed("")

    }

}