package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJacksonInject
import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.PropertyNamingStrategy
import org.cirjson.cirjackson.databind.configuration.ConstructorDetector
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import java.util.*

/**
 * Helper class used for aggregating information about all possible properties of a POJO.
 * 
 * @property myConfig Configuration settings
 * 
 * @property myForSerialization `true` if introspection is done for serialization (giving precedence for serialization
 * annotations), or not (`false`, deserialization)
 * 
 * @property myType Type of POJO for which properties are being collected.
 * 
 * @property myClassDefinition Low-level introspected class information (methods, fields, etc.)
 * 
 * @property myAccessorNaming Handler used for name-mangling of getter, mutator (setter/with) methods
 */
open class POJOPropertiesCollector protected constructor(protected val myConfig: MapperConfig<*>,
        protected val myForSerialization: Boolean, protected val myType: KotlinType,
        protected val myClassDefinition: AnnotatedClass, protected val myAccessorNaming: AccessorNamingStrategy) {

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    protected val myVisibilityChecker = myConfig.getDefaultVisibilityChecker(myType.rawClass, myClassDefinition)

    protected val myAnnotationIntrospector: AnnotationIntrospector

    protected val myUseAnnotations: Boolean

    protected val myIsRecordType = myType.isRecordType

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    /**
     * State flag we keep to indicate whether actual property information
     * has been collected or not.
     */
    protected var myCollected = false

    /**
     * Set of logical property information collected so far.
     */
    protected var myProperties: LinkedHashMap<String, POJOPropertyBuilder>? = null

    protected var myCreatorProperties: MutableList<POJOPropertyBuilder>? = null

    protected var myPotentialCreators: PotentialCreators? = null

    /**
     * A set of "field renamings" that have been discovered, indicating intended renaming of other accessors: key is the
     * implicit original name and value intended name to use instead.
     * 
     * Note that these renamings are applied earlier than "regular" (explicit) renamings and affect implicit name: their
     * effect may be changed by further renaming based on explicit indicators. The main use case is to effectively
     * relink accessors based on fields discovered, and used to sort of correct otherwise missing linkage between fields
     * and other accessors.
     */
    protected var myFieldRenameMappings: MutableMap<PropertyName, PropertyName>? = null

    protected var myAnyGetters: LinkedList<AnnotatedMember>? = null

    protected var myAnyGetterField: LinkedList<AnnotatedMember>? = null

    protected var myAnySetters: LinkedList<AnnotatedMethod>? = null

    protected var myAnySetterField: LinkedList<AnnotatedMember>? = null

    /**
     * Accessors (field or "getter" method annotated with [org.cirjson.cirjackson.annotations.CirJsonKey]
     */
    protected var myCirJsonKeyAccessors: LinkedList<AnnotatedMember>? = null

    /**
     * Accessors (field or "getter" method) annotated with [org.cirjson.cirjackson.annotations.CirJsonValue]
     */
    protected var myCirJsonValueAccessors: LinkedList<AnnotatedMember>? = null

    /**
     * Lazily collected set of properties that can be implicitly ignored during serialization; only updated when
     * collecting information for deserialization purposes
     */
    protected var myIgnoredPropertyNames: HashSet<String>? = null

    /**
     * Lazily collected map of members that were annotated to indicate that they represent mutators for deserializer
     * value injection.
     */
    protected var myInjectables: LinkedHashMap<Any, AnnotatedMember>? = null

    /**
     * Lazily accessed information about POJO format overrides
     */
    protected var myFormatOverrides: CirJsonFormat.Value? = null

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    init {
        if (myConfig.isAnnotationProcessingEnabled) {
            myUseAnnotations = true
            myAnnotationIntrospector = myConfig.annotationIntrospector!!
        } else {
            myUseAnnotations = false
            myAnnotationIntrospector = AnnotationIntrospector.nopInstance()
        }
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    open val config: MapperConfig<*>
        get() = TODO("Not yet implemented")

    open val type: KotlinType
        get() = TODO("Not yet implemented")

    open val isRecordType: Boolean
        get() = TODO("Not yet implemented")

    open val classDefinition: AnnotatedClass
        get() = TODO("Not yet implemented")

    open val annotationIntrospector: AnnotationIntrospector
        get() = TODO("Not yet implemented")

    open val properties: MutableList<BeanPropertyDefinition>
        get() = TODO("Not yet implemented")

    open val potentialCreators: PotentialCreators
        get() = TODO("Not yet implemented")

    open val injectables: Map<Any, AnnotatedMember>?
        get() = TODO("Not yet implemented")

    open val cirJsonKeyAccessor: AnnotatedMember?
        get() = TODO("Not yet implemented")

    open val cirJsonValueAccessor: AnnotatedMember?
        get() = TODO("Not yet implemented")

    open val anyGetterField: AnnotatedMember?
        get() = TODO("Not yet implemented")

    open val anyGetterMethod: AnnotatedMember?
        get() = TODO("Not yet implemented")

    open val anySetterField: AnnotatedMember?
        get() = TODO("Not yet implemented")

    open val anySetterMethod: AnnotatedMethod?
        get() = TODO("Not yet implemented")

    open val ignoredPropertyNames: Set<String>?
        get() = TODO("Not yet implemented")

    open val objectIdInfo: ObjectIdInfo
        get() = TODO("Not yet implemented")

    open val propertyMap: Map<String, POJOPropertyBuilder>
        get() = TODO("Not yet implemented")

    open val formatOverrides: CirJsonFormat.Value
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Public API: main-level collection
     *******************************************************************************************************************
     */

    /**
     * Internal method that will collect actual property information.
     */
    open fun collectAll() {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Property introspection: Fields
     *******************************************************************************************************************
     */

    /**
     * Method for collecting basic information on all fields found
     */
    protected open fun addFields(properties: MutableMap<String, POJOPropertyBuilder>) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Property introspection: Creators (constructors, factory methods)
     *******************************************************************************************************************
     */

    protected open fun addCreators(properties: MutableMap<String, POJOPropertyBuilder>) {
        TODO("Not yet implemented")
    }

    private fun isDelegatingConstructor(creator: PotentialCreator): Boolean {
        TODO("Not yet implemented")
    }

    private fun collectCreators(creators: List<AnnotatedWithParams>): List<PotentialCreator> {
        TODO("Not yet implemented")
    }

    private fun removeDisabledCreators(creators: List<PotentialCreator>) {
        TODO("Not yet implemented")
    }

    private fun removeNonVisibleCreators(creators: List<PotentialCreator>) {
        TODO("Not yet implemented")
    }

    private fun removeNonFactoryStaticMethods(creators: List<PotentialCreator>) {
        TODO("Not yet implemented")
    }

    private fun addExplicitlyAnnotatedCreators(collector: PotentialCreators, creators: List<PotentialCreator>,
            properties: MutableMap<String, POJOPropertyBuilder>, skipPropertiesBased: Boolean) {
        TODO("Not yet implemented")
    }

    private fun isExplicitlyAnnotatedCreatorPropertiesBased(collector: PotentialCreators,
            properties: MutableMap<String, POJOPropertyBuilder>, constructorDetector: ConstructorDetector): Boolean {
        TODO("Not yet implemented")
    }

    private fun addCreatorsWithAnnotatedNames(collector: PotentialCreators, creators: List<PotentialCreator>) {
        TODO("Not yet implemented")
    }

    private fun addImplicitConstructor(collector: PotentialCreators, creators: List<PotentialCreator>,
            properties: MutableMap<String, POJOPropertyBuilder>) {
        TODO("Not yet implemented")
    }

    private fun addCreatorParameters(properties: MutableMap<String, POJOPropertyBuilder>, creator: PotentialCreator,
            creatorProperties: List<POJOPropertyBuilder>) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Property introspection: Methods (getters, setters, etc.)
     *******************************************************************************************************************
     */

    /**
     * Method for collecting basic information on all accessor methods found
     */
    protected open fun addMethods(properties: MutableMap<String, POJOPropertyBuilder>) {
        TODO("Not yet implemented")
    }

    protected open fun addGetterMethod(properties: MutableMap<String, POJOPropertyBuilder>, method: AnnotatedMethod) {
        TODO("Not yet implemented")
    }

    protected open fun addSetterMethod(properties: MutableMap<String, POJOPropertyBuilder>, method: AnnotatedMethod) {
        TODO("Not yet implemented")
    }

    protected open fun addInjectables(properties: MutableMap<String, POJOPropertyBuilder>) {
        TODO("Not yet implemented")
    }

    protected open fun doAddInjectable(injectable: CirJacksonInject.Value?, member: AnnotatedMember) {
        TODO("Not yet implemented")
    }

    private fun propertyNameFromSimple(simpleName: String): PropertyName {
        TODO("Not yet implemented")
    }

    protected fun checkRenameByField(implementationName: String): String {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods: removing ignored properties
     *******************************************************************************************************************
     */

    /**
     * Method called to get rid of candidate properties that are marked as ignored.
     */
    protected open fun removeUnwantedProperties(properties: MutableMap<String, POJOPropertyBuilder>) {
        TODO("Not yet implemented")
    }

    /**
     * Method called to further get rid of unwanted individual accessors, based on read/write settings and rules for
     * "pulling in" accessors (or not).
     */
    protected open fun removeUnwantedAccessor(properties: MutableMap<String, POJOPropertyBuilder>) {
        TODO("Not yet implemented")
    }

    /**
     * Helper method called to add explicitly ignored properties to a list of known ignored properties; this helps in
     * proper reporting of errors.
     */
    protected open fun collectIgnorals(name: String?) {
        TODO("Not yet implemented")
    }

    /**
     * An internal accessor for [collectIgnorals]
     */
    internal fun internalCollectIgnorals(name: String?) {
        collectIgnorals(name)
    }

    /*
     *******************************************************************************************************************
     * Internal methods: renaming properties
     *******************************************************************************************************************
     */

    protected open fun renameProperties(properties: MutableMap<String, POJOPropertyBuilder>) {
        TODO("Not yet implemented")
    }

    protected open fun renameUsing(propertiesMap: MutableMap<String, POJOPropertyBuilder>,
            namingStrategy: PropertyNamingStrategy) {
        TODO("Not yet implemented")
    }

    /**
     * Helper method called to check if given property should be renamed using
     * [org.cirjson.cirjackson.databind.PropertyNamingStrategies].
     * 
     * NOTE: copied+simplified version of `BasicBeanDescription.findExpectedFormat()`.
     */
    private fun findFormatShape(): CirJsonFormat.Value? {
        TODO("Not yet implemented")
    }

    protected open fun renameWithWrappers(properties: MutableMap<String, POJOPropertyBuilder>) {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods: sorting
     *******************************************************************************************************************
     */

    protected open fun sortProperties(properties: MutableMap<String, POJOPropertyBuilder>) {
        TODO("Not yet implemented")
    }

    private fun anyIndexed(properties: Collection<POJOPropertyBuilder>): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods: conflict resolution
     *******************************************************************************************************************
     */

    protected open fun resolveFieldVsGetter(accessors: MutableList<AnnotatedMember>): Boolean {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Internal methods: helpers
     *******************************************************************************************************************
     */

    protected open fun reportProblem(message: String): Nothing {
        TODO("Not yet implemented")
    }

    protected open fun property(properties: MutableMap<String, POJOPropertyBuilder>,
            name: PropertyName): POJOPropertyBuilder {
        TODO("Not yet implemented")
    }

    protected open fun property(properties: MutableMap<String, POJOPropertyBuilder>,
            implementationName: String): POJOPropertyBuilder {
        TODO("Not yet implemented")
    }

    private fun findNamingStrategy(): PropertyNamingStrategy {
        TODO("Not yet implemented")
    }

    companion object {

        internal fun create(config: MapperConfig<*>, forSerialization: Boolean, type: KotlinType,
                classDefinition: AnnotatedClass, accessorNaming: AccessorNamingStrategy): POJOPropertiesCollector {
            return POJOPropertiesCollector(config, forSerialization, type, classDefinition, accessorNaming)
        }

    }

}