package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.annotations.CirJsonProperty
import org.cirjson.cirjackson.annotations.Nulls
import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyMetadata
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.emptyIterator
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import org.cirjson.cirjackson.databind.util.name
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

    protected var myConstructorParameters: Linked<AnnotatedParameter>?

    protected var myGetters: Linked<AnnotatedMethod>?

    protected var mySetters: Linked<AnnotatedMethod>?

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
        return POJOPropertyBuilder(this, newName)
    }

    override fun withSimpleName(newSimpleName: String): BeanPropertyDefinition {
        val newName = myName.withSimpleName(newSimpleName)

        if (newName === myName) {
            return this
        }

        return POJOPropertyBuilder(this, newName)
    }

    /*
     *******************************************************************************************************************
     * Comparable implementation
     *******************************************************************************************************************
     */

    override fun compareTo(other: POJOPropertyBuilder): Int {
        if (myConstructorParameters != null) {
            if (other.myConstructorParameters == null) {
                return -1
            }
        } else if (other.myConstructorParameters != null) {
            return 1
        }

        return name.compareTo(other.name)
    }

    /*
     *******************************************************************************************************************
     * BeanPropertyDefinition implementation, name/type
     *******************************************************************************************************************
     */

    override val name: String
        get() = myName.simpleName

    override val fullName: PropertyName
        get() = myName

    override fun hasName(name: PropertyName): Boolean {
        return myName == name
    }

    override val internalName: String
        get() = myInternalName.simpleName

    override val wrapperName: PropertyName?
        get() {
            val member = primaryMember ?: return null
            return myAnnotationIntrospector!!.findWrapperName(myConfig, member)
        }

    override val isExplicitlyIncluded: Boolean
        get() = anyExplicits(myFields) || anyExplicits(myGetters) || anyExplicits(mySetters) ||
                anyExplicitNames(myConstructorParameters)

    override val isExplicitlyNamed: Boolean
        get() = anyExplicitNames(myFields) || anyExplicitNames(myGetters) || anyExplicitNames(mySetters) ||
                anyExplicitNames(myConstructorParameters)

    /*
     *******************************************************************************************************************
     * BeanPropertyDefinition implementation, simple metadata
     *******************************************************************************************************************
     */

    override val metadata: PropertyMetadata
        get() {
            if (myMetadata != null) {
                return myMetadata!!
            }

            val primary = primaryMemberUnchecked ?: return PropertyMetadata.STANDARD_REQUIRED_OR_OPTIONAL.also {
                myMetadata = it
            }

            val required = myAnnotationIntrospector!!.hasRequiredMarker(myConfig, primary)
            val description = myAnnotationIntrospector.findPropertyDescription(myConfig, primary)
            val index = myAnnotationIntrospector.findPropertyIndex(myConfig, primary)
            val defaultValue = myAnnotationIntrospector.findPropertyDefaultValue(myConfig, primary)

            myMetadata = if (required == null && index == null && defaultValue == null) {
                if (description == null) {
                    PropertyMetadata.STANDARD_REQUIRED_OR_OPTIONAL
                } else {
                    PropertyMetadata.STANDARD_REQUIRED_OR_OPTIONAL.withDescription(description)
                }
            } else {
                PropertyMetadata.construct(required, description, index, defaultValue)
            }

            if (!myForSerialization) {
                myMetadata = getSetterInfo(myMetadata!!, primary)
            }

            return myMetadata!!
        }

    /**
     * Helper method that contains logic for accessing and merging all setter information that we needed, regarding
     * things like possible merging of property value, and handling of incoming nulls. Only called for deserialization
     * purposes.
     */
    protected open fun getSetterInfo(metadata: PropertyMetadata, primary: AnnotatedMember?): PropertyMetadata {
        var realMetadata = metadata

        var needMerge = true
        var valueNulls: Nulls? = null
        var contentNulls: Nulls? = null

        val accessor = accessor

        if (primary != null) {
            if (myAnnotationIntrospector != null) {
                if (accessor != null) {
                    val mergeInfo = myAnnotationIntrospector.findMergeInfo(myConfig, primary)

                    if (mergeInfo != null) {
                        needMerge = false

                        if (mergeInfo) {
                            realMetadata = realMetadata.withMergeInfo(
                                    PropertyMetadata.MergeInfo.createForPropertyOverride(accessor))
                        }
                    }
                }

                val setterInfo = myAnnotationIntrospector.findSetterInfo(myConfig, primary)

                if (setterInfo != null) {
                    valueNulls = setterInfo.nonDefaultNulls()
                    contentNulls = setterInfo.nonDefaultContentNulls()
                }
            }

            if (needMerge || valueNulls == null || contentNulls == null) {
                val rawType = rawTypeOf(primary)
                val configOverride = myConfig.getConfigOverride(rawType)
                val nullHandling = configOverride.nullHandling

                if (nullHandling != null) {
                    if (valueNulls == null) {
                        valueNulls = nullHandling.nonDefaultNulls()
                    }

                    if (contentNulls == null) {
                        contentNulls = nullHandling.nonDefaultContentNulls()
                    }
                }

                if (needMerge && accessor != null) {
                    val mergeable = configOverride.mergeable

                    if (mergeable != null) {
                        needMerge = false

                        if (mergeable) {
                            realMetadata = realMetadata.withMergeInfo(
                                    PropertyMetadata.MergeInfo.createForTypeOverride(accessor))
                        }
                    }
                }
            }
        }

        if (needMerge || valueNulls == null || contentNulls == null) {
            val setterInfo = myConfig.defaultNullHandling

            if (valueNulls == null) {
                valueNulls = setterInfo.nonDefaultNulls()
            }

            if (contentNulls == null) {
                contentNulls = setterInfo.nonDefaultContentNulls()
            }

            if (needMerge) {
                val defaultMergeable = myConfig.defaultMergeable

                if (defaultMergeable == true && accessor != null) {
                    realMetadata = realMetadata.withMergeInfo(PropertyMetadata.MergeInfo.createForDefaults(accessor))
                }
            }
        }

        if (valueNulls != null || contentNulls != null) {
            realMetadata = realMetadata.withNulls(valueNulls, contentNulls)
        }

        return realMetadata
    }

    /**
     * Type determined from the primary member for the property being built, considering precedence according to whether
     * we are processing serialization or deserialization.
     */
    override val primaryType: KotlinType
        get() {
            if (myForSerialization) {
                return (getter ?: this.field)?.type ?: TypeFactory.unknownType()
            }

            var member: AnnotatedMember? = constructorParameter

            if (member == null) {
                member = setter

                if (member != null) {
                    return member.getParameterType(0)!!
                }

                member = this.field
            }

            return (member ?: getter)?.type ?: TypeFactory.unknownType()
        }

    override val rawPrimaryType: KClass<*>
        get() = primaryType.rawClass

    /*
     *******************************************************************************************************************
     * BeanPropertyDefinition implementation, accessor access
     *******************************************************************************************************************
     */

    override fun hasGetter(): Boolean {
        return myGetters != null
    }

    override fun hasSetter(): Boolean {
        return mySetters != null
    }

    override fun hasField(): Boolean {
        return myFields != null
    }

    override fun hasConstructorParameter(): Boolean {
        return myConstructorParameters != null
    }

    override fun couldDeserialize(): Boolean {
        return myConstructorParameters != null || mySetters != null || anyVisible(myFields)
    }

    override fun couldSerialize(): Boolean {
        return myGetters != null || myFields != null
    }

    override val getter: AnnotatedMethod?
        get() {
            var current = myGetters ?: return null
            var next: Linked<AnnotatedMethod>? = current.next ?: return current.value

            while (next != null) {
                val currentClass = current.value.declaringClass
                val nextClass = next.value.declaringClass

                if (currentClass != nextClass) {
                    if (currentClass.isAssignableFrom(nextClass)) {
                        current = next
                        next = next.next
                        continue
                    }

                    if (nextClass.isAssignableFrom(currentClass)) {
                        next = next.next
                        continue
                    }
                }

                val primaryNext = getterPriority(next.value)
                val primaryCurrent = getterPriority(current.value)

                if (primaryNext == primaryCurrent) {
                    throw IllegalArgumentException(
                            "Conflicting getter definitions for property \"$name\": ${current.value.fullName} vs ${next.value.fullName}")
                }

                if (primaryNext < primaryCurrent) {
                    current = next
                }

                next = next.next
            }

            myGetters = current.withoutNext()
            return current.value
        }

    /**
     * Variant of [getter] that does NOT trigger pruning of getter candidates.
     */
    protected open val getterUnchecked: AnnotatedMethod?
        get() = myGetters?.value

    override val setter: AnnotatedMethod?
        get() {
            var current = mySetters ?: return null
            var next: Linked<AnnotatedMethod>? = current.next ?: return current.value

            while (next != null) {
                val selected = selectSetter(current.value, next.value)

                if (selected === current.value) {
                    next = next.next
                    continue
                }

                if (selected === next.value) {
                    current = next
                    next = next.next
                    continue
                }

                return selectSetterFromMultiple(current, next)
            }

            mySetters = current.withoutNext()
            return current.value
        }

    /**
     * Variant of [setter] that does NOT trigger pruning of setter candidates.
     */
    protected open val setterUnchecked: AnnotatedMethod?
        get() = mySetters?.value

    /**
     * Helper method called in cases where we have encountered two setter methods that have same precedence and cannot
     * be resolved. This does not yet necessarily mean a failure since it is possible something with a higher precedence
     * could still be found; handling is just separated into separate method for convenience.
     *
     * @return Chosen setter method, if any
     *
     * @throws IllegalArgumentException If conflict could not be resolved
     */
    protected open fun selectSetterFromMultiple(current: Linked<AnnotatedMethod>,
            next: Linked<AnnotatedMethod>): AnnotatedMethod {
        var realCurrent = current
        var realNext: Linked<AnnotatedMethod>? = next
        val conflicts = ArrayList<AnnotatedMethod>()
        conflicts.add(realCurrent.value)
        conflicts.add(realNext!!.value)

        realNext = realNext.next

        while (realNext != null) {
            val selected = selectSetter(realCurrent.value, realNext.value)

            if (selected === realCurrent.value) {
                realNext = realNext.next
                continue
            }

            if (selected === realNext.value) {
                conflicts.clear()
                realCurrent = realNext
                realNext = realNext.next
                continue
            }

            conflicts.add(realNext.value)
            realNext = realNext.next
        }

        if (conflicts.isNotEmpty()) {
            val description = conflicts.joinToString(" vs ") { it.fullName }
            throw IllegalArgumentException("Conflicting setter definitions for property \"$name\": $description")
        }

        mySetters = realCurrent.withoutNext()
        return realCurrent.value
    }

    protected open fun selectSetter(currentMethod: AnnotatedMethod, nextMethod: AnnotatedMethod): AnnotatedMethod? {
        val currentClass = currentMethod.declaringClass
        val nextClass = nextMethod.declaringClass

        if (currentClass != nextClass) {
            if (currentClass.isAssignableFrom(nextClass)) {
                return nextMethod
            }

            if (nextClass.isAssignableFrom(currentClass)) {
                return currentMethod
            }
        }

        val priorityNext = setterPriority(currentMethod)
        val priorityCurrent = getterPriority(currentMethod)

        if (priorityCurrent != priorityNext) {
            return if (priorityNext < priorityCurrent) nextMethod else currentMethod
        }

        return myAnnotationIntrospector?.resolveSetterConflict(myConfig, currentMethod, nextMethod)
    }

    override val field: AnnotatedField?
        get() {
            val fields = myFields ?: return null
            var field = fields.value
            var next = fields.next

            while (next != null) {
                val nextField = next.value
                val fieldClass = field.declaringClass
                val nextClass = nextField.declaringClass

                if (fieldClass != nextClass) {
                    if (fieldClass.isAssignableFrom(nextClass)) {
                        field = nextField
                        next = next.next
                        continue
                    }

                    if (nextClass.isAssignableFrom(fieldClass)) {
                        next = next.next
                        continue
                    }
                }

                val currentStatic = field.isStatic
                val nextStatic = nextField.isStatic

                if (nextStatic == currentStatic) {
                    throw IllegalArgumentException(
                            "Multiple fields representing property \"$name\": ${field.fullName} vs ${nextField.fullName}")
                }

                if (currentStatic) {
                    field = nextField
                }
            }

            return field
        }

    /**
     * Variant of [field] that does NOT trigger pruning of field candidates.
     */
    protected open val fieldUnchecked: AnnotatedField?
        get() = myFields?.value

    override val constructorParameter: AnnotatedParameter?
        get() {
            var current: Linked<AnnotatedParameter>? = myConstructorParameters ?: return null

            do {
                if (current!!.value.owner is AnnotatedConstructor) {
                    return current.value
                }

                current = current.next
            } while (current != null)

            return myConstructorParameters!!.value
        }

    override val constructorParameters: Iterator<AnnotatedParameter>
        get() = myConstructorParameters?.let { MemberIterator(it) } ?: emptyIterator()

    override val primaryMember: AnnotatedMember?
        get() = if (myForSerialization) accessor else mutator ?: accessor

    protected open val primaryMemberUnchecked: AnnotatedMember?
        get() = if (myForSerialization) {
            myGetters?.value ?: myFields?.value
        } else {
            myConstructorParameters?.value ?: mySetters?.value ?: myFields?.value ?: myGetters?.value
        }

    protected open fun getterPriority(method: AnnotatedMethod): Int {
        val name = method.name

        if (name.startsWith("get") && name.length > 3) {
            return 1
        }

        if (name.startsWith("is") && name.length > 2) {
            return 2
        }

        return 3
    }

    protected open fun setterPriority(method: AnnotatedMethod): Int {
        val name = method.name

        if (name.startsWith("set") && name.length > 3) {
            return 1
        }

        return 2
    }

    /*
     *******************************************************************************************************************
     * Implementations of refinement accessors
     *******************************************************************************************************************
     */

    override fun findViews(): Array<KClass<*>>? {
        return myAnnotationIntrospector!!.findViews(myConfig, primaryMember!!)
    }

    override fun findReferenceType(): AnnotationIntrospector.ReferenceProperty? {
        var result = myReferenceInfo

        if (result != null) {
            return result.takeUnless { it == NOT_REFERENCE_PROPERTY }
        }

        val member = primaryMemberUnchecked

        if (member != null) {
            result = myAnnotationIntrospector!!.findReferenceType(myConfig, member)
        }

        myReferenceInfo = result.takeUnless { it == NOT_REFERENCE_PROPERTY }
        return result
    }

    override val isTypeId: Boolean
        get() {
            val member = primaryMemberUnchecked ?: return false
            return myAnnotationIntrospector!!.isTypeId(myConfig, member) ?: false
        }

    override fun findObjectIdInfo(): ObjectIdInfo? {
        val member = primaryMemberUnchecked ?: return null
        val info = myAnnotationIntrospector!!.findObjectIdInfo(myConfig, member) ?: return null
        return myAnnotationIntrospector.findObjectReferenceInfo(myConfig, member, info)
    }

    override fun findInclusion(): CirJsonInclude.Value {
        return myAnnotationIntrospector!!.findPropertyInclusion(myConfig, accessor!!) ?: CirJsonInclude.Value.EMPTY
    }

    override fun findAliases(): List<PropertyName> {
        val member = primaryMember ?: return emptyList()
        return myAnnotationIntrospector!!.findPropertyAliases(myConfig, member) ?: emptyList()
    }

    open fun findAccess(): CirJsonProperty.Access? {
        return fromMemberAnnotationsExcept(CirJsonProperty.Access.AUTO) {
            myAnnotationIntrospector!!.findPropertyAccess(myConfig, it)
        }
    }

    /*
     *******************************************************************************************************************
     * Data aggregation
     *******************************************************************************************************************
     */

    open fun addField(field: AnnotatedField, name: PropertyName?, explicitName: Boolean, visible: Boolean,
            ignored: Boolean) {
        myFields = Linked(field, myFields, name, explicitName, visible, ignored)
    }

    open fun addConstructorParameter(parameter: AnnotatedParameter, name: PropertyName?, explicitName: Boolean,
            visible: Boolean, ignored: Boolean) {
        myConstructorParameters = Linked(parameter, myConstructorParameters, name, explicitName, visible, ignored)
    }

    open fun addGetter(method: AnnotatedMethod, name: PropertyName?, explicitName: Boolean, visible: Boolean,
            ignored: Boolean) {
        myGetters = Linked(method, myGetters, name, explicitName, visible, ignored)
    }

    open fun addSetter(method: AnnotatedMethod, name: PropertyName?, explicitName: Boolean, visible: Boolean,
            ignored: Boolean) {
        mySetters = Linked(method, mySetters, name, explicitName, visible, ignored)
    }

    /**
     * Method for adding all property members from specified collector into this collector.
     */
    open fun addAll(source: POJOPropertyBuilder) {
        myFields = merge(myFields, source.myFields)
        myConstructorParameters = merge(myConstructorParameters, source.myConstructorParameters)
        myGetters = merge(myGetters, source.myGetters)
        mySetters = merge(mySetters, source.mySetters)
    }

    /*
     *******************************************************************************************************************
     * Modifications
     *******************************************************************************************************************
     */

    /**
     * Method called to remove all entries that are marked as ignored.
     */
    open fun removeIgnored() {
        myFields = removeIgnored(myFields)
        myConstructorParameters = removeIgnored(myConstructorParameters)
        myGetters = removeIgnored(myGetters)
        mySetters = removeIgnored(mySetters)
    }

    /**
     * @param inferMutators Whether mutators can be "pulled in" by visible accessors or not.
     */
    open fun removeNonVisible(inferMutators: Boolean, parent: POJOPropertiesCollector?): CirJsonProperty.Access {
        val access = findAccess() ?: CirJsonProperty.Access.AUTO

        when (access) {
            CirJsonProperty.Access.READ_ONLY -> {
                if (parent != null) {
                    parent.internalCollectIgnorals(name)

                    for (propertyName in findExplicitNames()) {
                        parent.internalCollectIgnorals(propertyName.simpleName)
                    }
                }

                mySetters = null
                myConstructorParameters = null

                if (!myForSerialization) {
                    myFields = null
                }
            }

            CirJsonProperty.Access.READ_WRITE -> {}

            CirJsonProperty.Access.WRITE_ONLY -> {
                myGetters = null

                if (myForSerialization) {
                    myFields = null
                }
            }

            CirJsonProperty.Access.AUTO -> {
                myGetters = removeNonVisible(myGetters)
                myConstructorParameters = removeNonVisible(myConstructorParameters)

                if (!inferMutators || myGetters == null) {
                    myFields = removeNonVisible(myFields)
                    mySetters = removeNonVisible(mySetters)
                }
            }
        }

        return access
    }

    /**
     * Mutator that will simply drop any constructor parameters property may have.
     */
    open fun removeConstructors() {
        myConstructorParameters = null
    }

    open fun trimByVisibility() {
        myFields = trimByVisibility(myFields)
        myConstructorParameters = trimByVisibility(myConstructorParameters)
        myGetters = trimByVisibility(myGetters)
        mySetters = trimByVisibility(mySetters)
    }

    open fun mergeAnnotations(forSerialization: Boolean) {
        if (forSerialization) {
            if (myGetters != null) {
                val annotations = mergeAnnotations(myGetters,
                        mergeAnnotations(myFields, mergeAnnotations(myConstructorParameters, mySetters)))
                myGetters = applyAnnotations(myGetters!!, annotations)
            } else if (myFields != null) {
                val annotations = mergeAnnotations(myFields, mergeAnnotations(myConstructorParameters, mySetters))
                myFields = applyAnnotations(myFields!!, annotations)
            }

            return
        }

        if (myConstructorParameters != null) {
            val annotations = mergeAnnotations(myConstructorParameters,
                    mergeAnnotations(mySetters, mergeAnnotations(myFields, myGetters)))
            myConstructorParameters = applyAnnotations(myConstructorParameters!!, annotations)
        } else if (mySetters != null) {
            val annotations = mergeAnnotations(mySetters, mergeAnnotations(myFields, myGetters))
            mySetters = applyAnnotations(mySetters!!, annotations)
        } else if (myFields != null) {
            val annotations = mergeAnnotations(myFields, myGetters)
            myFields = applyAnnotations(myFields!!, annotations)
        }
    }

    private fun mergeAnnotations(node1: Linked<out AnnotatedMember>?,
            node2: Linked<out AnnotatedMember>?): AnnotationMap? {
        return AnnotationMap.merge(getAllAnnotations(node1), getAllAnnotations(node2))
    }

    private fun mergeAnnotations(node1: Linked<out AnnotatedMember>?, secondary: AnnotationMap?): AnnotationMap? {
        return AnnotationMap.merge(getAllAnnotations(node1), secondary)
    }

    /**
     * Replacement of simple access to annotations, which does "deep merge" if an as necessary, across alternate
     * accessors of same type: most importantly, "is-getter vs regular getter"
     */
    private fun getAllAnnotations(node: Linked<out AnnotatedMember>?): AnnotationMap? {
        node ?: return null
        val annotations = node.value.allAnnotations
        return node.next?.let { AnnotationMap.merge(annotations, getAllAnnotations(it)) } ?: annotations
    }

    /**
     * Helper method to handle recursive merging of annotations within accessor class, to ensure no annotations are
     * accidentally dropped within chain when non-visible and secondary accessors are pruned later on.
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : AnnotatedMember> applyAnnotations(node: Linked<T>, annotations: AnnotationMap?): Linked<T> {
        val value = node.value.withAnnotations(annotations) as T
        return (node.next?.let { node.withNext(applyAnnotations(it, annotations)) } ?: node).withValue(value)
    }

    private fun <T : Any> removeIgnored(node: Linked<T>?): Linked<T>? {
        return node?.withoutIgnored()
    }

    private fun <T : Any> removeNonVisible(node: Linked<T>?): Linked<T>? {
        return node?.withoutNonVisible()
    }

    private fun <T : Any> trimByVisibility(node: Linked<T>?): Linked<T>? {
        return node?.trimByVisibility()
    }

    /*
     *******************************************************************************************************************
     * Accessors for aggregate information
     *******************************************************************************************************************
     */

    private fun <T : Any> anyExplicits(node: Linked<T>?): Boolean {
        var next: Linked<T>? = node

        while (next != null) {
            if (next.name?.hasSimpleName() ?: false) {
                return true
            }

            next = next.next
        }

        return false
    }

    private fun <T : Any> anyExplicitNames(node: Linked<T>?): Boolean {
        var next: Linked<T>? = node

        while (next != null) {
            if (next.name != null && next.isNameExplicit) {
                return true
            }

            next = next.next
        }

        return false
    }

    open fun anyVisible(): Boolean {
        return anyVisible(myFields) || anyVisible(myGetters) || anyVisible(mySetters) ||
                anyVisible(myConstructorParameters)
    }

    private fun <T : Any> anyVisible(node: Linked<T>?): Boolean {
        var next: Linked<T>? = node

        while (next != null) {
            if (next.isVisible) {
                return true
            }

            next = next.next
        }

        return false
    }

    open fun anyIgnorals(): Boolean {
        return anyIgnorals(myFields) || anyIgnorals(myGetters) || anyIgnorals(mySetters) ||
                anyIgnorals(myConstructorParameters)
    }

    private fun <T : Any> anyIgnorals(node: Linked<T>?): Boolean {
        var next: Linked<T>? = node

        while (next != null) {
            if (next.isMarkedIgnored) {
                return true
            }

            next = next.next
        }

        return false
    }

    open fun anyExplicitsWithoutIgnoral(): Boolean {
        return anyExplicitsWithoutIgnoral(myFields) || anyExplicitsWithoutIgnoral(
                myGetters) || anyExplicitsWithoutIgnoral(mySetters) ||
                anyExplicitNamesWithoutIgnoral(myConstructorParameters)
    }

    private fun <T : Any> anyExplicitsWithoutIgnoral(node: Linked<T>?): Boolean {
        var next: Linked<T>? = node

        while (next != null) {
            if (!next.isMarkedIgnored && next.name?.hasSimpleName() ?: false) {
                return true
            }

            next = next.next
        }

        return false
    }

    private fun <T : Any> anyExplicitNamesWithoutIgnoral(node: Linked<T>?): Boolean {
        var next: Linked<T>? = node

        while (next != null) {
            if (!next.isMarkedIgnored && next.name != null && next.isNameExplicit) {
                return true
            }

            next = next.next
        }

        return false
    }

    /**
     * Method called to find out set of explicit names for accessors bound together due to implicit name.
     */
    open fun findExplicitNames(): Set<PropertyName> {
        var renamed = findExplicitNames(myFields, null)
        renamed = findExplicitNames(myGetters, renamed)
        renamed = findExplicitNames(mySetters, renamed)
        return findExplicitNames(myConstructorParameters, renamed) ?: emptySet()
    }

    /**
     * Method called when a previous call to [findExplicitNames] found multiple distinct explicit names, and the
     * property this builder represents basically needs to be broken apart and replaced by a set of more than one
     * property.
     */
    open fun explode(newNames: Collection<PropertyName>): Collection<POJOPropertyBuilder> {
        val properties = HashMap<PropertyName, POJOPropertyBuilder>()
        explode(newNames, properties, myFields)
        explode(newNames, properties, myGetters)
        explode(newNames, properties, mySetters)
        explode(newNames, properties, myConstructorParameters)
        return properties.values
    }

    @Suppress("UNCHECKED_CAST")
    private fun explode(newNames: Collection<PropertyName>, properties: MutableMap<PropertyName, POJOPropertyBuilder>,
            accessors: Linked<*>?) {
        var node = accessors

        while (node != null) {
            val name = node.name

            if (!node.isNameExplicit || name == null) {
                if (!node.isVisible) {
                    node = node.next
                    continue
                }

                throw IllegalStateException(
                        "Conflicting/ambiguous property name definitions (implicit name ${myName.name()}): found multiple explicit names: $newNames, but also implicit accessor: $node")
            }

            var property = properties[name]

            if (property == null) {
                property = POJOPropertyBuilder(myConfig, myAnnotationIntrospector, myForSerialization, myInternalName,
                        name)
                properties[name] = property
            }

            if (accessors === myFields) {
                val n2 = node as Linked<AnnotatedField>
                property.myFields = n2.withNext(property.myFields)
            } else if (accessors === myGetters) {
                val n2 = node as Linked<AnnotatedMethod>
                property.myGetters = n2.withNext(property.myGetters)
            } else if (accessors === mySetters) {
                val n2 = node as Linked<AnnotatedMethod>
                property.mySetters = n2.withNext(property.mySetters)
            } else if (accessors === myConstructorParameters) {
                val n2 = node as Linked<AnnotatedParameter>
                property.myConstructorParameters = n2.withNext(property.myConstructorParameters)
            } else {
                throw IllegalStateException("Internal error: mismatched accessors, property: $this")
            }

            node = node.next
        }
    }

    private fun findExplicitNames(node: Linked<out AnnotatedMember>?,
            renamed: MutableSet<PropertyName>?): MutableSet<PropertyName>? {
        var realNode = node
        var realRenamed = renamed

        while (realNode != null) {
            if (!realNode.isNameExplicit || realNode.name == null) {
                realNode = realNode.next
                continue
            }

            if (realRenamed == null) {
                realRenamed = HashSet()
            }

            realRenamed.add(realNode.name)
        }

        return realRenamed
    }

    override fun toString(): String {
        return "[Property '$name'; ctors: $myConstructorParameters, field(s): $myFields, getter(s): $myGetters, setter(s): $mySetters]"
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    protected open fun <T> fromMemberAnnotations(function: WithMember<T>): T? {
        myAnnotationIntrospector ?: return null

        var result: T? = null

        if (myForSerialization) {
            if (myGetters != null) {
                result = function.withMember(myGetters!!.value)
            }
        } else {
            if (myConstructorParameters != null) {
                result = function.withMember(myConstructorParameters!!.value)
            }

            if (result == null && mySetters != null) {
                result = function.withMember(mySetters!!.value)
            }
        }

        if (result == null && myFields != null) {
            result = function.withMember(myFields!!.value)
        }

        return result
    }

    @Suppress("SameParameterValue")
    protected open fun <T> fromMemberAnnotationsExcept(defaultValue: T, function: WithMember<T>): T? {
        if (myForSerialization) {
            if (myGetters != null) {
                val result = function.withMember(myGetters!!.value)

                if (result != null && result != defaultValue) {
                    return result
                }
            }

            if (myFields != null) {
                val result = function.withMember(myFields!!.value)

                if (result != null && result != defaultValue) {
                    return result
                }
            }

            if (myConstructorParameters != null) {
                val result = function.withMember(myConstructorParameters!!.value)

                if (result != null && result != defaultValue) {
                    return result
                }
            }

            if (mySetters != null) {
                val result = function.withMember(mySetters!!.value)

                if (result != null && result != defaultValue) {
                    return result
                }
            }

            return null
        }

        if (myConstructorParameters != null) {
            val result = function.withMember(myConstructorParameters!!.value)

            if (result != null && result != defaultValue) {
                return result
            }
        }

        if (mySetters != null) {
            val result = function.withMember(mySetters!!.value)

            if (result != null && result != defaultValue) {
                return result
            }
        }

        if (myFields != null) {
            val result = function.withMember(myFields!!.value)

            if (result != null && result != defaultValue) {
                return result
            }
        }

        if (myGetters != null) {
            val result = function.withMember(myGetters!!.value)

            if (result != null && result != defaultValue) {
                return result
            }
        }

        return null
    }

    protected open fun rawTypeOf(member: AnnotatedMember): KClass<*> {
        if (member is AnnotatedMethod && member.parameterCount > 0) {
            return member.getParameterType(0)!!.rawClass
        }

        return member.type.rawClass
    }

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

        fun withValue(newValue: T): Linked<T> {
            if (value === newValue) {
                return this
            }

            return Linked(newValue, next, name, isNameExplicit, isVisible, isMarkedIgnored)
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
        fun append(appendable: Linked<T>): Linked<T> {
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

        private fun <T : Any> merge(chain1: Linked<T>?, chain2: Linked<T>?): Linked<T>? {
            chain1 ?: return chain2
            chain2 ?: return chain1
            return chain1.append(chain2)
        }

    }

}