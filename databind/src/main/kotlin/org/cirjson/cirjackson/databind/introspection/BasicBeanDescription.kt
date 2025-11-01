package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJsonCreator
import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJsonPOJOBuilder
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.util.*
import kotlin.reflect.KClass

/**
 * Default [BeanDescription] implementation used by CirJackson.
 *
 * Although subclassing is a theoretical possibility, there are no known use cases for that, nor is such usage tested or
 * supported. Separation from API is mostly to isolate some implementation details here and keep API simple.
 */
open class BasicBeanDescription : BeanDescription {

    /*
     *******************************************************************************************************************
     * General configuration
     *******************************************************************************************************************
     */

    /**
     * A reference to the collector in cases where information is lazily accessed and constructed; properties are only
     * accessed when they are actually needed.
     */
    protected val myPropertiesCollector: POJOPropertiesCollector?

    protected val myConfig: MapperConfig<*>

    protected val myIntrospector: AnnotationIntrospector

    /*
     *******************************************************************************************************************
     * Information about type itself
     *******************************************************************************************************************
     */

    /**
     * Information collected about the class introspected.
     */
    protected val myClassInfo: AnnotatedClass

    protected var myDefaultViews: Array<KClass<*>>? = null

    protected var myDefaultViewsResolved = false

    /*
     *******************************************************************************************************************
     * Member information
     *******************************************************************************************************************
     */

    /**
     * Properties collected for the POJO; initialized as needed.
     */
    protected var myProperties: MutableList<BeanPropertyDefinition>? = null

    /**
     * Details of Object ID to include, if any
     */
    protected var myObjectIdInfo: ObjectIdInfo? = null

    /*
     *******************************************************************************************************************
     * Lazily accessed results of introspection, cached for reuse
     *******************************************************************************************************************
     */

    /**
     * Results of introspecting `@CirJsonFormat` configuration for class, if any.
     */
    @Transient
    protected var myClassFormat: CirJsonFormat.Value? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    protected constructor(collector: POJOPropertiesCollector, type: KotlinType,
            classDefinition: AnnotatedClass) : super(type) {
        myPropertiesCollector = collector
        myConfig = collector.config
        myIntrospector = collector.annotationIntrospector
        myClassInfo = classDefinition
    }

    /**
     * Alternate constructor used in cases where property information is not needed, only class info.
     */
    protected constructor(config: MapperConfig<*>, type: KotlinType, classDefinition: AnnotatedClass) : super(type) {
        myPropertiesCollector = null
        myConfig = config
        myIntrospector = config.annotationIntrospector!!
        myClassInfo = classDefinition
    }

    protected constructor(collector: POJOPropertiesCollector) : this(collector, collector.type,
            collector.classDefinition) {
        myObjectIdInfo = collector.objectIdInfo
    }

    protected open fun properties(): MutableList<BeanPropertyDefinition> {
        return myProperties ?: myPropertiesCollector!!.properties.also { myProperties = it }
    }

    /*
     *******************************************************************************************************************
     * Limited modifications by core databind functionality
     *******************************************************************************************************************
     */

    /**
     * Method that can be used to prune unwanted properties, during construction of serializers and deserializers. Use
     * with utmost care, if at all...
     */
    open fun removeProperty(propertyName: String): Boolean {
        val iterator = properties().iterator()

        while (iterator.hasNext()) {
            val property = iterator.next()

            if (property.name == propertyName) {
                iterator.remove()
                return true
            }
        }

        return false
    }

    open fun addProperty(definition: BeanPropertyDefinition): Boolean {
        if (hasProperty(definition.fullName)) {
            return false
        }

        properties().add(definition)
        return true
    }

    open fun hasProperty(name: PropertyName): Boolean {
        return findProperty(name) != null
    }

    open fun findProperty(name: PropertyName): BeanPropertyDefinition? {
        for (property in properties()) {
            if (property.hasName(name)) {
                return property
            }
        }

        return null
    }

    /*
     *******************************************************************************************************************
     * Simple accessors from BeanDescription
     *******************************************************************************************************************
     */

    override val classInfo: AnnotatedClass
        get() = myClassInfo

    override val objectIdInfo: ObjectIdInfo?
        get() = myObjectIdInfo

    override fun findProperties(): List<BeanPropertyDefinition> {
        return properties()
    }

    override fun findCirJsonKeyAccessor(): AnnotatedMember? {
        return myPropertiesCollector?.cirJsonKeyAccessor
    }

    override fun findCirJsonValueAccessor(): AnnotatedMember? {
        return myPropertiesCollector?.cirJsonValueAccessor
    }

    override val ignoredPropertyNames: Set<String>
        get() = myPropertiesCollector?.ignoredPropertyNames ?: emptySet()

    override fun hasKnownClassAnnotations(): Boolean {
        return myClassInfo.hasAnnotations()
    }

    override val classAnnotations: Annotations
        get() = myClassInfo.annotations

    override fun findDefaultConstructor(): AnnotatedConstructor? {
        return myClassInfo.defaultConstructor
    }

    @Throws(IllegalArgumentException::class)
    override fun findAnySetterAccessor(): AnnotatedMember? {
        myPropertiesCollector ?: return null

        val anyMethod = myPropertiesCollector.anySetterMethod

        if (anyMethod != null) {
            val type = anyMethod.getRawParameterType(0)!!

            if (type != String::class && type != Any::class) {
                throw IllegalArgumentException(
                        "Invalid 'any-setter' annotation on method '${anyMethod.name}()': first argument not of type String or Object, but ${type.qualifiedName}")
            }

            return anyMethod
        }

        val anyField = myPropertiesCollector.anySetterField ?: return null
        val type = anyField.rawType

        if (!Map::class.isAssignableFrom(type) && !CirJsonNode::class.isAssignableFrom(type)) {
            throw IllegalArgumentException(
                    "Invalid 'any-setter' annotation on field '${anyField.name}': type is not instance of `Map` or `CirJsonNode`")
        }

        return anyField
    }

    override fun findInjectables(): Map<Any, AnnotatedMember> {
        return myPropertiesCollector?.injectables ?: emptyMap()
    }

    override val constructors: List<AnnotatedConstructor>
        get() = myClassInfo.constructors

    override val constructorsWithMode: List<AnnotatedAndMetadata<AnnotatedConstructor, CirJsonCreator.Mode>>
        get() {
            val allConstructors = myClassInfo.constructors

            if (allConstructors.isEmpty()) {
                return emptyList()
            }

            val result = ArrayList<AnnotatedAndMetadata<AnnotatedConstructor, CirJsonCreator.Mode>>()

            for (constructor in allConstructors) {
                val mode = myIntrospector.findCreatorAnnotation(myConfig, constructor)

                if (mode == CirJsonCreator.Mode.DISABLED) {
                    continue
                }

                result.add(AnnotatedAndMetadata.of(constructor, mode))
            }

            return result
        }

    override val potentialCreators: PotentialCreators
        get() = myPropertiesCollector?.potentialCreators ?: PotentialCreators()

    override fun instantiateBean(fixAccess: Boolean): Any? {
        val annotatedConstructor = myClassInfo.defaultConstructor ?: return null

        if (fixAccess) {
            annotatedConstructor.fixAccess(myConfig.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
        }

        try {
            return annotatedConstructor.call()
        } catch (e: Exception) {
            var t: Throwable = e

            while (t.cause != null) {
                t = t.cause!!
            }

            t.throwIfError()
            t.throwIfRuntimeException()
            throw IllegalArgumentException(
                    "Failed to instantiate bean of type ${myClassInfo.annotated.name}: (${t::class.qualifiedName}) ${t.exceptionMessage()}",
                    t)
        }
    }

    /*
     *******************************************************************************************************************
     * Simple accessors, extended
     *******************************************************************************************************************
     */

    override fun findMethod(name: String, paramTypes: Array<KClass<*>>): AnnotatedMethod? {
        return myClassInfo.findMethod(name, paramTypes)
    }

    /*
     *******************************************************************************************************************
     * General per-class annotation introspection
     *******************************************************************************************************************
     */

    override fun findExpectedFormat(baseType: KClass<*>): CirJsonFormat.Value? {
        val v0 = myClassFormat ?: (myIntrospector.findFormat(myConfig, myClassInfo)
                ?: CirJsonFormat.Value.EMPTY).also { myClassFormat = it }
        val v1 = myConfig.getDefaultPropertyFormat(baseType)
        return CirJsonFormat.Value.merge(v0, v1)
    }

    override fun findDefaultViews(): Array<KClass<*>>? {
        if (myDefaultViewsResolved) {
            return myDefaultViews
        }

        var default = myIntrospector.findViews(myConfig, myClassInfo)

        if (default == null && myConfig.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION)) {
            default = NO_VIEWS
        }

        return default.also { myDefaultViews = it }
    }

    /*
     *******************************************************************************************************************
     * Introspection for serialization
     *******************************************************************************************************************
     */

    override fun findSerializationConverter(): Converter<Any, Any>? {
        return createConverter(myIntrospector.findSerializationConverter(myConfig, myClassInfo))
    }

    /**
     * Method for determining whether null properties should be written out for a Bean of introspected type. This is
     * based on global feature (lowest priority, passed as argument) and per-class annotation (highest priority).
     */
    override fun findPropertyInclusion(defaultValue: CirJsonInclude.Value?): CirJsonInclude.Value? {
        val include = myIntrospector.findPropertyInclusion(myConfig, myClassInfo) ?: return defaultValue
        return defaultValue?.withOverrides(include) ?: include
    }

    /**
     * Method used to locate the method of introspected class that implements
     * [org.cirjson.cirjackson.annotations.CirJsonAnyGetter]. If no such method exists, `null` is returned. If more than
     * one are found, an exception is thrown.
     */
    @Throws(IllegalArgumentException::class)
    override fun findAnyGetter(): AnnotatedMember? {
        myPropertiesCollector ?: return null

        val anyGetter = myPropertiesCollector.anyGetterMethod

        if (anyGetter != null) {
            val type = anyGetter.rawType

            if (!Map::class.isAssignableFrom(type)) {
                throw IllegalArgumentException(
                        "Invalid 'any-getter' annotation on method ${anyGetter.name}(): return type is not instance of Map")
            }

            return anyGetter
        }

        val anyField = myPropertiesCollector.anyGetterField ?: return null
        val type = anyField.rawType

        if (!Map::class.isAssignableFrom(type)) {
            throw IllegalArgumentException(
                    "Invalid 'any-getter' annotation on field '${anyField.name}': type is not instance of Map")
        }

        return anyField
    }

    override fun findBackReferences(): List<BeanPropertyDefinition>? {
        var result: MutableList<BeanPropertyDefinition>? = null
        var names: MutableSet<String>? = null

        for (property in properties()) {
            val reference = property.findReferenceType()

            if (reference == null || !reference.isBackReference) {
                continue
            }

            val referenceName = reference.name

            if (result == null) {
                result = ArrayList()
                names = HashSet()
                names.add(referenceName)
            } else if (!names!!.add(referenceName)) {
                throw IllegalArgumentException("Multiple back-reference properties with name ${referenceName.name()}")
            }

            result.add(property)
        }

        return result
    }

    /*
     *******************************************************************************************************************
     * Introspection for deserialization, factories
     *******************************************************************************************************************
     */

    override val factoryMethods: List<AnnotatedMethod>
        get() {
            val candidates = myClassInfo.factoryMethods

            if (candidates.isEmpty()) {
                return candidates
            }

            var result: MutableList<AnnotatedMethod>? = null

            for (annotatedMethod in candidates) {
                if (!isFactoryMethod(annotatedMethod)) {
                    continue
                }

                if (result == null) {
                    result = ArrayList()
                }

                result.add(annotatedMethod)
            }

            return result ?: emptyList()
        }

    override val factoryMethodsWithMode: List<AnnotatedAndMetadata<AnnotatedMethod, CirJsonCreator.Mode>>
        get() {
            val candidates = myClassInfo.factoryMethods

            if (candidates.isEmpty()) {
                return emptyList()
            }

            var result: MutableList<AnnotatedAndMetadata<AnnotatedMethod, CirJsonCreator.Mode>>? = null

            for (annotatedMethod in candidates) {
                val match = findFactoryMethodMetadata(annotatedMethod) ?: continue

                if (result == null) {
                    result = ArrayList()
                }

                result.add(match)
            }

            return result ?: emptyList()
        }

    protected open fun isFactoryMethod(annotatedMethod: AnnotatedMethod): Boolean {
        val returnType = annotatedMethod.rawReturnType

        if (!beanClass.isAssignableFrom(returnType)) {
            return false
        }

        val mode = myIntrospector.findCreatorAnnotation(myConfig, annotatedMethod)

        if (mode != null && mode != CirJsonCreator.Mode.DISABLED) {
            return true
        }

        val name = annotatedMethod.name

        if (name == "valueOf" && annotatedMethod.parameterCount == 1) {
            return true
        }

        if (name == "fromString" && annotatedMethod.parameterCount == 1) {
            val clazz = annotatedMethod.getRawParameterType(0)!!
            return clazz == String::class || CharSequence::class.isAssignableFrom(clazz)
        }

        return false
    }

    protected open fun findFactoryMethodMetadata(
            annotatedMethod: AnnotatedMethod): AnnotatedAndMetadata<AnnotatedMethod, CirJsonCreator.Mode>? {
        val returnType = annotatedMethod.rawReturnType

        if (!beanClass.isAssignableFrom(returnType)) {
            return null
        }

        val mode = myIntrospector.findCreatorAnnotation(myConfig, annotatedMethod)

        if (mode != null) {
            if (mode == CirJsonCreator.Mode.DISABLED) {
                return null
            }

            return AnnotatedAndMetadata.of(annotatedMethod, mode)
        }

        val name = annotatedMethod.name

        if (name == "valueOf" && annotatedMethod.parameterCount == 1) {
            return AnnotatedAndMetadata.of(annotatedMethod, null)
        }

        if (name == "fromString" && annotatedMethod.parameterCount == 1) {
            val clazz = annotatedMethod.getRawParameterType(0)!!

            if (clazz == String::class || CharSequence::class.isAssignableFrom(clazz)) {
                return AnnotatedAndMetadata.of(annotatedMethod, null)
            }
        }

        return null
    }

    /*
     *******************************************************************************************************************
     * Introspection for deserialization, other
     *******************************************************************************************************************
     */

    override fun findPOJOBuilder(): KClass<*>? {
        return myIntrospector.findPOJOBuilder(myConfig, myClassInfo)
    }

    override fun findPOJOBuilderConfig(): CirJsonPOJOBuilder.Value? {
        return myIntrospector.findPOJOBuilderConfig(myConfig, myClassInfo)
    }

    override fun findDeserializationConverter(): Converter<Any, Any>? {
        return createConverter(myIntrospector.findDeserializationConverter(myConfig, myClassInfo))
    }

    override fun findClassDescription(): String? {
        return myIntrospector.findClassDescription(myConfig, myClassInfo)
    }

    /*
     *******************************************************************************************************************
     * Helper methods, other
     *******************************************************************************************************************
     */

    @Suppress("UNCHECKED_CAST")
    protected open fun createConverter(converterDefinition: Any?): Converter<Any, Any>? {
        converterDefinition ?: return null

        if (converterDefinition is Converter<*, *>) {
            return converterDefinition as Converter<Any, Any>
        }

        if (converterDefinition !is KClass<*>) {
            throw IllegalStateException(
                    "AnnotationIntrospector returned Converter definition of type ${converterDefinition::class.qualifiedName}; expected type Converter or KClass<Converter> instead")
        }

        if (converterDefinition == Converter.None::class || converterDefinition.isBogusClass) {
            return null
        }

        if (!Converter::class.isAssignableFrom(converterDefinition)) {
            throw IllegalStateException(
                    "AnnotationIntrospector returned KClass ${converterDefinition.qualifiedName}; expected KClass<Converter>")
        }

        val handlerInstantiator = myConfig.handlerInstantiator
        return (handlerInstantiator?.converterInstance(myConfig, myClassInfo, converterDefinition)
                ?: converterDefinition.createInstance(myConfig.canOverrideAccessModifiers())) as Converter<Any, Any>
    }

    companion object {

        private val NO_VIEWS = emptyArray<KClass<*>>()

        /**
         * Factory method to use for constructing an instance to use for building deserializers.
         */
        fun forDeserialization(collector: POJOPropertiesCollector): BasicBeanDescription {
            return BasicBeanDescription(collector)
        }

        /**
         * Factory method to use for constructing an instance to use for building serializers.
         */
        fun forSerialization(collector: POJOPropertiesCollector): BasicBeanDescription {
            return BasicBeanDescription(collector)
        }

        /**
         * Factory method to use for constructing an instance to use for purposes other than building serializers or
         * deserializers; will only have information on class, not on properties.
         */
        fun forOtherUse(config: MapperConfig<*>, type: KotlinType,
                annotatedClass: AnnotatedClass): BasicBeanDescription {
            return BasicBeanDescription(config, type, annotatedClass)
        }

    }

}