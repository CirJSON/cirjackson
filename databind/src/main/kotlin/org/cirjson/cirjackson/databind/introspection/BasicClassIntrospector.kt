package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.CirJsonNode
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import org.cirjson.cirjackson.databind.util.isJdkClass
import org.cirjson.cirjackson.databind.util.isPrimitive

open class BasicClassIntrospector : ClassIntrospector {

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    protected val myMixInResolver: MixInResolver?

    protected val myConfig: MapperConfig<*>?

    /*
     *******************************************************************************************************************
     * State
     *******************************************************************************************************************
     */

    /**
     * Reuse fully-resolved annotations during a single operation
     */
    protected var myResolvedFullAnnotations: HashMap<KotlinType, AnnotatedClass>? = null

    /**
     * Reuse full bean descriptions for serialization during a single operation
     */
    protected var myResolvedSerializationBeanDescriptions: HashMap<KotlinType, BasicBeanDescription>? = null

    /**
     * Reuse full bean descriptions for deserialization during a single operation
     */
    protected var myResolvedDeserializationBeanDescriptions: HashMap<KotlinType, BasicBeanDescription>? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    constructor() : super() {
        myMixInResolver = null
        myConfig = null
    }

    protected constructor(config: MapperConfig<*>) : super() {
        myMixInResolver = config
        myConfig = config
    }

    override fun forMapper(): BasicClassIntrospector {
        return this
    }

    override fun forOperation(config: MapperConfig<*>): BasicClassIntrospector {
        return BasicClassIntrospector(config)
    }

    /*
     *******************************************************************************************************************
     * Factory method implementations: annotation resolution
     *******************************************************************************************************************
     */

    override fun introspectClassAnnotations(type: KotlinType): AnnotatedClass {
        var annotatedClass = findStandardTypeDefinition(type)

        if (annotatedClass != null) {
            return annotatedClass
        }

        if (myResolvedFullAnnotations == null) {
            myResolvedFullAnnotations = HashMap()
        } else {
            annotatedClass = myResolvedFullAnnotations!![type]

            if (annotatedClass != null) {
                return annotatedClass
            }
        }

        annotatedClass = resolveAnnotatedClass(type)
        myResolvedFullAnnotations!![type] = annotatedClass
        return annotatedClass
    }

    override fun introspectDirectClassAnnotations(type: KotlinType): AnnotatedClass {
        return findStandardTypeDefinition(type) ?: resolveAnnotatedWithoutSuperTypes(type)
    }

    protected open fun resolveAnnotatedClass(type: KotlinType): AnnotatedClass {
        return AnnotatedClassResolver.resolve(myConfig, type, myMixInResolver)
    }

    protected open fun resolveAnnotatedWithoutSuperTypes(type: KotlinType): AnnotatedClass {
        return AnnotatedClassResolver.resolveWithoutSuperTypes(myConfig, type, myMixInResolver)
    }

    /*
     *******************************************************************************************************************
     * Factory method implementations: bean introspection
     *******************************************************************************************************************
     */

    override fun introspectForSerialization(type: KotlinType): BasicBeanDescription {
        var description = findStandardTypeDescription(type)

        if (description != null) {
            return description
        }

        description = findStandardCollectionDescription(type)

        if (description != null) {
            return description
        }

        if (myResolvedSerializationBeanDescriptions == null) {
            myResolvedSerializationBeanDescriptions = HashMap()
        } else {
            description = myResolvedSerializationBeanDescriptions!![type]

            if (description != null) {
                return description
            }
        }

        description = BasicBeanDescription.forSerialization(
                collectProperties(type, introspectClassAnnotations(type), true, "set"))
        myResolvedSerializationBeanDescriptions!![type] = description
        return description
    }

    override fun introspectForDeserialization(type: KotlinType): BasicBeanDescription {
        var description = findStandardTypeDescription(type)

        if (description != null) {
            return description
        }

        description = findStandardCollectionDescription(type)

        if (description != null) {
            return description
        }

        if (myResolvedDeserializationBeanDescriptions == null) {
            myResolvedDeserializationBeanDescriptions = HashMap()
        } else {
            description = myResolvedDeserializationBeanDescriptions!![type]

            if (description != null) {
                return description
            }
        }

        description = BasicBeanDescription.forDeserialization(
                collectProperties(type, introspectClassAnnotations(type), false, "set"))
        myResolvedDeserializationBeanDescriptions!![type] = description
        return description
    }

    override fun introspectForDeserializationWithBuilder(type: KotlinType,
            valueTypeDescription: BeanDescription): BasicBeanDescription {
        return BasicBeanDescription.forDeserialization(
                collectPropertiesWithBuilder(type, introspectClassAnnotations(type), valueTypeDescription, false))
    }

    override fun introspectForCreation(type: KotlinType): BasicBeanDescription {
        val description = findStandardTypeDescription(type)

        if (description != null) {
            return description
        }

        return findStandardCollectionDescription(type) ?: BasicBeanDescription.forDeserialization(
                collectProperties(type, introspectClassAnnotations(type), false, "set"))
    }

    /*
     *******************************************************************************************************************
     * Overridable helper methods
     *******************************************************************************************************************
     */

    open fun collectProperties(type: KotlinType, classDefinition: AnnotatedClass, forSerialization: Boolean,
            mutatorPrefix: String): POJOPropertiesCollector {
        val accessorNaming = if (type.isRecordType) {
            myConfig!!.accessorNaming.forRecord(myConfig, classDefinition)
        } else {
            myConfig!!.accessorNaming.forPOJO(myConfig, classDefinition)
        }

        return constructPropertyCollector(type, classDefinition, forSerialization, accessorNaming)
    }

    open fun collectPropertiesWithBuilder(type: KotlinType, builderClassDefinition: AnnotatedClass,
            valueTypeDescription: BeanDescription, forSerialization: Boolean): POJOPropertiesCollector {
        val accessorNaming =
                myConfig!!.accessorNaming.forBuilder(myConfig, builderClassDefinition, valueTypeDescription)
        return constructPropertyCollector(type, builderClassDefinition, forSerialization, accessorNaming)
    }

    /**
     * Overridable method called for creating [POJOPropertiesCollector] instance to use; override is needed if a custom
     * subclass is to be used.
     */
    open fun constructPropertyCollector(type: KotlinType, classDefinition: AnnotatedClass, forSerialization: Boolean,
            accessorNaming: AccessorNamingStrategy): POJOPropertiesCollector {
        return POJOPropertiesCollector.create(myConfig!!, forSerialization, type, classDefinition, accessorNaming)
    }

    open fun findStandardTypeDescription(type: KotlinType): BasicBeanDescription? {
        return findStandardTypeDefinition(type)?.let { BasicBeanDescription.forOtherUse(myConfig!!, type, it) }
    }

    /**
     * Method called to see if type is one of core types that have been cached for efficiency.
     */
    open fun findStandardTypeDefinition(type: KotlinType): AnnotatedClass? {
        val rawType = type.rawClass
        return when {
            rawType.isPrimitive -> when (rawType) {
                Int::class -> INT_ANNOTATED_CLASS
                Long::class -> LONG_ANNOTATED_CLASS
                Boolean::class -> BOOLEAN_ANNOTATED_CLASS
                else -> null
            }

            rawType.isJdkClass -> when (rawType) {
                CLASS_STRING -> STRING_ANNOTATED_CLASS
                Int::class -> INT_ANNOTATED_CLASS
                Long::class -> LONG_ANNOTATED_CLASS
                Boolean::class -> BOOLEAN_ANNOTATED_CLASS
                CLASS_OBJECT -> OBJECT_ANNOTATED_CLASS
                CLASS_NUMBER -> NUMBER_ANNOTATED_CLASS
                else -> null
            }

            CLASS_CIR_JSON_NODE.isAssignableFrom(rawType) -> AnnotatedClass(rawType)

            else -> null
        }
    }

    open fun findStandardCollectionDescription(type: KotlinType): BasicBeanDescription? {
        if (!isStandardCollection(type)) {
            return null
        }

        return BasicBeanDescription.forOtherUse(myConfig!!, type, introspectClassAnnotations(type))
    }

    /**
     * Helper method used to decide whether we can omit introspection for members (methods, fields, constructors); we
     * may do so for a limited number of container types JDK provides.
     */
    private fun isStandardCollection(type: KotlinType): Boolean {
        if (!type.isContainerType || type.isArrayType) {
            return false
        }

        val raw = type.rawClass

        if (!raw.isJdkClass) {
            return false
        }

        if (Collection::class.isAssignableFrom(raw) || Map::class.isAssignableFrom(raw)) {
            return raw.toString().indexOf('$') <= 0
        }

        return false
    }

    companion object {

        private val CLASS_OBJECT = Any::class

        private val CLASS_STRING = String::class

        private val CLASS_NUMBER = Number::class

        private val CLASS_CIR_JSON_NODE = CirJsonNode::class

        private val OBJECT_ANNOTATED_CLASS = AnnotatedClass(CLASS_OBJECT)

        private val STRING_ANNOTATED_CLASS = AnnotatedClass(CLASS_STRING)

        private val BOOLEAN_ANNOTATED_CLASS = AnnotatedClass(Boolean::class)

        private val INT_ANNOTATED_CLASS = AnnotatedClass(Int::class)

        private val LONG_ANNOTATED_CLASS = AnnotatedClass(Long::class)

        private val NUMBER_ANNOTATED_CLASS = AnnotatedClass(CLASS_NUMBER)

    }

}