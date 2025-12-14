package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJacksonInject
import org.cirjson.cirjackson.annotations.CirJsonCreator
import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.configuration.ConstructorDetector
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.record.findCanonicalRecordConstructor
import org.cirjson.cirjackson.databind.util.className
import org.cirjson.cirjackson.databind.util.createInstance
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import java.lang.reflect.Modifier
import java.util.*
import kotlin.reflect.KClass

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

    protected var myCreatorProperties: MutableList<POJOPropertyBuilder?>? = null

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
        get() = myConfig

    open val type: KotlinType
        get() = myType

    open val isRecordType: Boolean
        get() = myIsRecordType

    open val classDefinition: AnnotatedClass
        get() = myClassDefinition

    open val annotationIntrospector: AnnotationIntrospector
        get() = myAnnotationIntrospector

    open val properties: MutableList<BeanPropertyDefinition>
        get() = ArrayList(propertyMap.values)

    open val potentialCreators: PotentialCreators
        get() {
            if (!myCollected) {
                collectAll()
            }

            return myPotentialCreators!!
        }

    open val injectables: Map<Any, AnnotatedMember>?
        get() {
            if (!myCollected) {
                collectAll()
            }

            return myInjectables!!
        }

    open val cirJsonKeyAccessor: AnnotatedMember?
        get() {
            if (!myCollected) {
                collectAll()
            }

            val accessors = myCirJsonKeyAccessors ?: return null

            if (accessors.size > 1 && !resolveFieldVsGetter(accessors)) {
                reportProblem("Multiple 'as-key' properties defined (${accessors[0]} vs ${accessors[1]})")
            }

            return accessors[0]
        }

    open val cirJsonValueAccessor: AnnotatedMember?
        get() {
            if (!myCollected) {
                collectAll()
            }

            val accessors = myCirJsonValueAccessors ?: return null

            if (accessors.size > 1 && !resolveFieldVsGetter(accessors)) {
                reportProblem("Multiple 'as-value' properties defined (${accessors[0]} vs ${accessors[1]})")
            }

            return accessors[0]
        }

    open val anyGetterField: AnnotatedMember?
        get() {
            if (!myCollected) {
                collectAll()
            }

            val fields = myAnyGetterField ?: return null

            if (fields.size > 1) {
                reportProblem("Multiple 'as-getter' properties defined (${fields[0]} vs ${fields[1]})")
            }

            return fields.first()
        }

    open val anyGetterMethod: AnnotatedMember?
        get() {
            if (!myCollected) {
                collectAll()
            }

            val getters = myAnyGetters ?: return null

            if (getters.size > 1) {
                reportProblem("Multiple 'as-getter' properties defined (${getters[0]} vs ${getters[1]})")
            }

            return getters.first()
        }

    open val anySetterField: AnnotatedMember?
        get() {
            if (!myCollected) {
                collectAll()
            }

            val fields = myAnySetterField ?: return null

            if (fields.size > 1) {
                reportProblem("Multiple 'as-setter' properties defined (${fields[0]} vs ${fields[1]})")
            }

            return fields.first()
        }

    open val anySetterMethod: AnnotatedMethod?
        get() {
            if (!myCollected) {
                collectAll()
            }

            val setters = myAnySetters ?: return null

            if (setters.size > 1) {
                reportProblem("Multiple 'as-getter' properties defined (${setters[0]} vs ${setters[1]})")
            }

            return setters.first()
        }

    /**
     * Accessor for set of properties that are explicitly marked to be ignored via per-property markers (but NOT class
     * annotations).
     */
    open val ignoredPropertyNames: Set<String>?
        get() = myIgnoredPropertyNames

    /**
     * Accessor to find out whether type specified requires inclusion of Object Identifier.
     */
    open val objectIdInfo: ObjectIdInfo?
        get() {
            val info = myAnnotationIntrospector.findObjectIdInfo(myConfig, myClassDefinition)
            return info?.let { myAnnotationIntrospector.findObjectReferenceInfo(myConfig, myClassDefinition, it) }
        }

    protected open val propertyMap: Map<String, POJOPropertyBuilder>
        get() {
            if (!myCollected) {
                collectAll()
            }

            return myProperties!!
        }

    open val formatOverrides: CirJsonFormat.Value
        get() {
            val overrides = myFormatOverrides

            if (overrides != null) {
                return overrides
            }

            val format = myAnnotationIntrospector.findFormat(myConfig, myClassDefinition)
            val value = myConfig.getDefaultPropertyFormat(myType.rawClass)
            return (format?.withOverrides(value) ?: value).also { myFormatOverrides = it }
        }

    /*
     *******************************************************************************************************************
     * Public API: main-level collection
     *******************************************************************************************************************
     */

    /**
     * Internal method that will collect actual property information.
     */
    open fun collectAll() {
        myPotentialCreators = PotentialCreators()
        val properties = LinkedHashMap<String, POJOPropertyBuilder>()

        if (!isRecordType || myForSerialization) {
            addFields(properties)
        }

        addMethods(properties)

        if (!myClassDefinition.isNonStaticInnerClass) {
            addCreators(properties)
        }

        removeUnwantedProperties(properties)
        removeUnwantedAccessor(properties)
        renameProperties(properties)

        addInjectables(properties)

        for (property in properties.values) {
            property.mergeAnnotations(myForSerialization)
        }

        val namingStrategy = findNamingStrategy()

        if (namingStrategy != null) {
            renameUsing(properties, namingStrategy)
        }

        for (property in properties.values) {
            property.trimByVisibility()
        }

        if (myConfig.isEnabled(MapperFeature.USE_WRAPPER_NAME_AS_PROPERTY_NAME)) {
            renameWithWrappers(properties)
        }

        sortProperties(properties)
        myProperties = properties
        myCollected = true
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
        val annotationIntrospector = myAnnotationIntrospector
        val pruneFinalFields = !myForSerialization && !myConfig.isEnabled(MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS)
        val transientAsIgnoral = myConfig.isEnabled(MapperFeature.PROPAGATE_TRANSIENT_MARKER)

        for (field in myClassDefinition.fields()) {
            if (annotationIntrospector.hasAsKey(myConfig, field) ?: false) {
                (myCirJsonKeyAccessors ?: LinkedList<AnnotatedMember>().also { myCirJsonKeyAccessors = it }).add(field)
            }

            if (annotationIntrospector.hasAsValue(myConfig, field) ?: false) {
                (myCirJsonValueAccessors ?: LinkedList<AnnotatedMember>().also { myCirJsonValueAccessors = it }).add(
                        field)
                continue
            }

            val anyGetter = annotationIntrospector.hasAnyGetter(myConfig, field) ?: false
            val anySetter = annotationIntrospector.hasAnySetter(myConfig, field) ?: false

            if (anyGetter || anySetter) {
                if (anyGetter) {
                    (myAnyGetterField ?: LinkedList<AnnotatedMember>().also { myAnyGetterField = it }).add(field)
                }

                if (anySetter) {
                    (myAnySetterField ?: LinkedList<AnnotatedMember>().also { myAnySetterField = it }).add(field)
                }

                continue
            }

            var implicitName: String? = annotationIntrospector.findImplicitPropertyName(myConfig, field) ?: field.name
            implicitName = myAccessorNaming.modifyFieldName(field, implicitName!!) ?: continue

            val implicitNameProperty = propertyNameFromSimple(implicitName)
            val rename = annotationIntrospector.findRenameByField(myConfig, field, implicitNameProperty)

            if (rename != null && rename != implicitNameProperty) {
                (myFieldRenameMappings ?: HashMap<PropertyName, PropertyName>().also {
                    myFieldRenameMappings = it
                })[rename] = implicitNameProperty
            }

            var propertyName = if (myForSerialization) {
                annotationIntrospector.findNameForSerialization(myConfig, field)
            } else {
                annotationIntrospector.findNameForDeserialization(myConfig, field)
            }

            val hasName = propertyName != null
            var nameExplicit = hasName

            if (nameExplicit && propertyName.isEmpty()) {
                propertyName = propertyNameFromSimple(implicitName)
                nameExplicit = false
            }

            var visible = propertyName != null

            if (!visible) {
                visible = myVisibilityChecker.isFieldVisible(field)
            }

            var ignored = annotationIntrospector.hasIgnoreMarker(myConfig, field)

            if (field.isTransient && !hasName) {
                if (transientAsIgnoral) {
                    ignored = true
                } else if (!ignored) {
                    continue
                }
            }

            if (pruneFinalFields && propertyName == null && !ignored && Modifier.isFinal(field.modifiers)) {
                continue
            }

            property(properties, implicitName).addField(field, propertyName, nameExplicit, visible, ignored)
        }
    }

    /*
     *******************************************************************************************************************
     * Property introspection: Creators (constructors, factory methods)
     *******************************************************************************************************************
     */

    protected open fun addCreators(properties: MutableMap<String, POJOPropertyBuilder>) {
        val creators = myPotentialCreators!!

        val constructors = collectCreators(myClassDefinition.constructors)
        val factories = collectCreators(myClassDefinition.factoryMethods)

        val canonical = if (myIsRecordType) {
            myClassDefinition.findCanonicalRecordConstructor(myConfig, constructors)
        } else {
            null
        }

        removeDisabledCreators(constructors)
        removeDisabledCreators(factories)
        removeNonFactoryStaticMethods(factories)

        if (myUseAnnotations) {
            addExplicitlyAnnotatedCreators(creators, constructors, properties, false)
            addExplicitlyAnnotatedCreators(creators, factories, properties, creators.hasPropertiesBased())
        }

        if (!creators.hasPropertiesBased()) {
            addCreatorsWithAnnotatedNames(creators, constructors)

            if (canonical != null && constructors.remove(canonical)) {
                if (isDelegatingConstructor(canonical)) {
                    creators.addExplicitDelegating(canonical)
                } else {
                    creators.setPropertiesBased(myConfig, canonical, "canonical")
                }
            }
        }

        val constructorDetector = myConfig.constructorDetector

        if (!creators.hasPropertiesBasedOrDelegating() && !constructorDetector.requireConstructorAnnotation() &&
                (myClassDefinition.defaultConstructor == null ||
                        constructorDetector.singleArgCreatorDefaultsToProperties())) {
            addImplicitConstructor(creators, constructors, properties)
        }

        removeNonVisibleCreators(constructors)
        removeNonVisibleCreators(factories)

        val primary = creators.propertiesBased

        if (primary == null) {
            myCreatorProperties = mutableListOf()
        } else {
            myCreatorProperties = ArrayList()
            addCreatorParameters(properties, primary, myCreatorProperties!!)
        }
    }

    private fun isDelegatingConstructor(creator: PotentialCreator): Boolean {
        if (creator.parameterCount() != 1) {
            return false
        }

        return !myCirJsonValueAccessors.isNullOrEmpty()
    }

    private fun collectCreators(creators: List<AnnotatedWithParams>): MutableList<PotentialCreator> {
        if (creators.isEmpty()) {
            return mutableListOf()
        }

        val result = ArrayList<PotentialCreator>()

        for (creator in creators) {
            val creatorMode = if (myUseAnnotations) {
                myAnnotationIntrospector.findCreatorAnnotation(myConfig, creator)
            } else {
                null
            }
            result.add(PotentialCreator(creator, creatorMode))
        }

        return result
    }

    private fun removeDisabledCreators(creators: MutableList<PotentialCreator>) {
        creators.removeIf { it.creatorMode() == CirJsonCreator.Mode.DISABLED }
    }

    private fun removeNonVisibleCreators(creators: MutableList<PotentialCreator>) {
        creators.removeIf {
            val visible = if (it.parameterCount() == 1) {
                myVisibilityChecker.isScalarConstructorVisible(it.creator())
            } else {
                myVisibilityChecker.isCreatorVisible(it.creator())
            }

            !visible
        }
    }

    private fun removeNonFactoryStaticMethods(creators: MutableList<PotentialCreator>) {
        val rawType = myType.rawClass
        creators.removeIf {
            if (it.creatorMode() != null) {
                return@removeIf false
            }

            val factory = it.creator()

            if (!rawType.isAssignableFrom(factory.rawType) || it.parameterCount() != 1) {
                return@removeIf true
            }

            val name = factory.name

            if (name == "valueOf") {
                return@removeIf false
            } else if (name == "fromString") {
                val clazz = factory.getRawParameterType(0)!!

                if (clazz == String::class || CharSequence::class.isAssignableFrom(clazz)) {
                    return@removeIf false
                }
            }

            false
        }
    }

    private fun addExplicitlyAnnotatedCreators(collector: PotentialCreators,
            constructors: MutableList<PotentialCreator>, properties: MutableMap<String, POJOPropertyBuilder>,
            skipPropertiesBased: Boolean) {
        val constructorDetector = myConfig.constructorDetector
        val iterator = constructors.iterator()

        while (iterator.hasNext()) {
            val constructor = iterator.next()

            constructor.creatorMode() ?: continue

            iterator.remove()

            val isPropertiesBased = when (constructor.creatorMode()) {
                CirJsonCreator.Mode.DELEGATING -> false
                CirJsonCreator.Mode.PROPERTIES -> true
                else -> isExplicitlyAnnotatedCreatorPropertiesBased(constructor, properties, constructorDetector)
            }

            if (isPropertiesBased) {
                if (!skipPropertiesBased) {
                    collector.setPropertiesBased(myConfig, constructor, "explicit")
                }
            } else {
                collector.addExplicitDelegating(constructor)
            }
        }
    }

    private fun isExplicitlyAnnotatedCreatorPropertiesBased(constructor: PotentialCreator,
            properties: MutableMap<String, POJOPropertyBuilder>, constructorDetector: ConstructorDetector): Boolean {
        if (constructor.parameterCount() == 1) {
            when (constructorDetector.singleArgMode()) {
                ConstructorDetector.SingleArgConstructor.DELEGATING -> return false

                ConstructorDetector.SingleArgConstructor.PROPERTIES -> return true

                ConstructorDetector.SingleArgConstructor.REQUIRE_MODE -> throw IllegalArgumentException(
                        "Single-argument constructor (${constructor.creator()}) is annotated but no 'mode' defined; `ConstructorDetector` configured with `SingleArgConstructor.REQUIRE_MODE`")

                ConstructorDetector.SingleArgConstructor.HEURISTIC -> {}
            }
        }

        constructor.introspectParameterNames(myConfig)

        if (constructor.hasExplicitNames()) {
            return true
        }

        if (!myCirJsonValueAccessors.isNullOrEmpty()) {
            return false
        }

        if (constructor.parameterCount() != 1) {
            return constructor.hasNameOrInjectForAllParameters(myConfig)
        }

        val implicitName = constructor.implicitNameSimple(0)

        if (implicitName != null) {
            val property = properties[implicitName]

            if (property != null && property.anyVisible() && !property.anyIgnorals()) {
                return true
            }
        }

        return myAnnotationIntrospector.findInjectableValue(myConfig, constructor.parameter(0)) != null
    }

    private fun addCreatorsWithAnnotatedNames(collector: PotentialCreators,
            constructors: MutableList<PotentialCreator>) {
        constructors.removeIf {
            it.introspectParameterNames(myConfig)

            if (!it.hasExplicitNames()) {
                return@removeIf false
            }

            collector.setPropertiesBased(myConfig, it, "implicit")

            true
        }
    }

    private fun addImplicitConstructor(collector: PotentialCreators, constructors: MutableList<PotentialCreator>,
            properties: MutableMap<String, POJOPropertyBuilder>) {
        if (constructors.size != 1) {
            return
        }

        val constructor = constructors.first()

        val visible = if (constructor.parameterCount() == 1) {
            myVisibilityChecker.isScalarConstructorVisible(constructor.creator())
        } else {
            myVisibilityChecker.isCreatorVisible(constructor.creator())
        }

        if (!visible) {
            return
        }

        constructor.introspectParameterNames(myConfig)

        if (constructor.parameterCount() != 1) {
            if (!constructor.hasNameOrInjectForAllParameters(myConfig)) {
                return
            }
        } else if (myAnnotationIntrospector.findInjectableValue(myConfig, constructor.parameter(0)) == null) {
            val constructorDetector = myConfig.constructorDetector

            if (constructorDetector.singleArgCreatorDefaultsToDelegating()) {
                return
            }

            if (!constructorDetector.singleArgCreatorDefaultsToProperties()) {
                val property = properties[constructor.implicitNameSimple(0)] ?: return

                if (!property.anyVisible() || property.anyIgnorals()) {
                    return
                }
            }
        }

        constructors.removeFirst()
        collector.setPropertiesBased(myConfig, constructor, "implicit")
    }

    private fun addCreatorParameters(properties: MutableMap<String, POJOPropertyBuilder>, creator: PotentialCreator,
            creatorProperties: MutableList<POJOPropertyBuilder?>) {
        for (i in 0..<creator.parameterCount()) {
            val parameter = creator.parameter(i)
            val explicitName = creator.explicitName(i)
            var implicitName = creator.implicitName(i)
            val hasExplicit = explicitName != null

            val property = if (!hasExplicit && implicitName == null) {
                null
            } else {
                if (implicitName != null) {
                    val name = checkRenameByField(implicitName.simpleName)
                    implicitName = PropertyName.construct(name)
                }

                val prop = implicitName?.let { property(properties, it) } ?: property(properties, explicitName!!)
                prop.addConstructorParameter(parameter, explicitName ?: implicitName, hasExplicit, visible = true,
                        ignored = false)
                prop
            }

            creatorProperties.add(property)
        }

        creator.assignPropertyDefinitions(creatorProperties)
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
        for (method in myClassDefinition.memberMethods()) {
            val argumentsCount = method.parameterCount

            if (argumentsCount == 0) {
                addGetterMethod(properties, method)
            } else if (argumentsCount == 1) {
                addSetterMethod(properties, method)
            } else if (argumentsCount == 2) {
                if (myAnnotationIntrospector.hasAnySetter(myConfig, method) ?: false) {
                    (myAnySetters ?: LinkedList<AnnotatedMethod>().also { myAnySetters = it }).add(method)
                }
            }
        }
    }

    protected open fun addGetterMethod(properties: MutableMap<String, POJOPropertyBuilder>, method: AnnotatedMethod) {
        val returnType = method.rawReturnType

        if (returnType == Unit::class ||
                returnType == Void::class && myConfig.isEnabled(MapperFeature.ALLOW_VOID_VALUED_PROPERTIES)) {
            return
        }

        if (myAnnotationIntrospector.hasAnyGetter(myConfig, method) ?: false) {
            (myAnyGetters ?: LinkedList<AnnotatedMember>().also { myAnyGetters = it }).add(method)
        }

        if (myAnnotationIntrospector.hasAsValue(myConfig, method) ?: false) {
            (myCirJsonValueAccessors ?: LinkedList<AnnotatedMember>().also { myCirJsonValueAccessors = it }).add(method)
        }

        var propertyName = myAnnotationIntrospector.findNameForSerialization(myConfig, method)
        var nameExplicit = propertyName != null

        var implicitName = myAnnotationIntrospector.findImplicitPropertyName(myConfig, method)

        val visible = if (!nameExplicit) {
            if (implicitName == null) {
                implicitName = myAccessorNaming.findNameForRegularGetter(method, method.name)
            }

            if (implicitName == null) {
                implicitName = myAccessorNaming.findNameForIsGetter(method, method.name) ?: return
                myVisibilityChecker.isIsGetterVisible(method)
            } else {
                myVisibilityChecker.isGetterVisible(method)
            }
        } else {
            if (implicitName == null) {
                implicitName = myAccessorNaming.findNameForRegularGetter(method, method.name)
                        ?: myAccessorNaming.findNameForIsGetter(method, method.name) ?: method.name
            }

            if (propertyName!!.isEmpty()) {
                propertyName = propertyNameFromSimple(implicitName)
                nameExplicit = false
            }

            true
        }

        implicitName = checkRenameByField(implicitName)
        val ignore = myAnnotationIntrospector.hasIgnoreMarker(myConfig, method)
        property(properties, implicitName).addGetter(method, propertyName, nameExplicit, visible, ignore)
    }

    protected open fun addSetterMethod(properties: MutableMap<String, POJOPropertyBuilder>, method: AnnotatedMethod) {
        var propertyName = myAnnotationIntrospector.findNameForDeserialization(myConfig, method)
        var nameExplicit = propertyName != null
        var implicitName = myAnnotationIntrospector.findImplicitPropertyName(myConfig, method)
                ?: myAccessorNaming.findNameForMutator(method, method.name)

        val visible = if (!nameExplicit) {
            implicitName ?: return
            myVisibilityChecker.isSetterVisible(method)
        } else {
            if (implicitName == null) {
                implicitName = method.name
            }

            if (propertyName!!.isEmpty()) {
                propertyName = propertyNameFromSimple(implicitName)
                nameExplicit = false
            }

            true
        }

        implicitName = checkRenameByField(implicitName)
        val ignore = myAnnotationIntrospector.hasIgnoreMarker(myConfig, method)
        property(properties, implicitName).addSetter(method, propertyName, nameExplicit, visible, ignore)
    }

    protected open fun addInjectables(properties: MutableMap<String, POJOPropertyBuilder>) {
        for (field in myClassDefinition.fields()) {
            doAddInjectable(myAnnotationIntrospector.findInjectableValue(myConfig, field), field)
        }

        for (method in myClassDefinition.memberMethods()) {
            if (method.parameterCount != 1) {
                continue
            }

            doAddInjectable(myAnnotationIntrospector.findInjectableValue(myConfig, method), method)
        }
    }

    protected open fun doAddInjectable(injectable: CirJacksonInject.Value?, member: AnnotatedMember) {
        injectable ?: return
        val id = injectable.id!!

        if (myInjectables == null) {
            myInjectables = LinkedHashMap()
        }

        val prev = myInjectables!!.put(id, member)

        if (prev != null) {
            if (prev::class == member::class) {
                reportProblem("Duplicate injectable value with id '$id' (of type ${id.className})")
            }
        }
    }

    private fun propertyNameFromSimple(simpleName: String): PropertyName {
        return PropertyName.construct(simpleName, null)
    }

    protected open fun checkRenameByField(implicitName: String): String {
        return myFieldRenameMappings?.get(propertyNameFromSimple(implicitName))?.simpleName ?: implicitName
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
        val iterator = properties.values.iterator()

        while (iterator.hasNext()) {
            val property = iterator.next()

            if (!property.anyVisible()) {
                iterator.remove()
                continue
            }

            if (!property.anyIgnorals()) {
                continue
            }

            if (isRecordType && !myForSerialization) {
                property.removeIgnored()
                collectIgnorals(property.name)
                continue
            }

            if (!property.anyExplicitsWithoutIgnoral()) {
                iterator.remove()
                collectIgnorals(property.name)
                continue
            }

            property.removeIgnored()

            if (!property.couldDeserialize()) {
                collectIgnorals(property.name)
            }
        }
    }

    /**
     * Method called to further get rid of unwanted individual accessors, based on read/write settings and rules for
     * "pulling in" accessors (or not).
     */
    protected open fun removeUnwantedAccessor(properties: MutableMap<String, POJOPropertyBuilder>) {
        val inferMutators = isRecordType && myConfig.isEnabled(MapperFeature.INFER_PROPERTY_MUTATORS)

        for (property in properties.values) {
            property.removeNonVisible(inferMutators, this.takeUnless { myForSerialization })
        }
    }

    /**
     * Helper method called to add explicitly ignored properties to a list of known ignored properties; this helps in
     * proper reporting of errors.
     */
    protected open fun collectIgnorals(name: String?) {
        if (myForSerialization || name == null) {
            return
        }

        (myIgnoredPropertyNames ?: HashSet<String>().also { myIgnoredPropertyNames = it }).add(name)
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
        val iterator = properties.iterator()
        var renamed: LinkedList<POJOPropertyBuilder>? = null

        while (iterator.hasNext()) {
            val entry = iterator.next()
            val property = entry.value
            val explicitNames = property.findExplicitNames()

            if (explicitNames.isEmpty()) {
                continue
            }

            iterator.remove()

            if (renamed == null) {
                renamed = LinkedList()
            }

            if (explicitNames.size == 1) {
                val name = explicitNames.first()
                renamed.add(property.withName(name))
                continue
            }

            renamed.addAll(property.explode(explicitNames))
        }

        renamed ?: return

        for (property in renamed) {
            val name = property.name
            val old = properties[name]

            if (old == null) {
                properties[name] = property
            } else {
                old.addAll(property)
            }

            if (replaceCreatorProperty(myCreatorProperties, property)) {
                myIgnoredPropertyNames?.remove(name)
            }
        }
    }

    protected open fun renameUsing(propertiesMap: MutableMap<String, POJOPropertyBuilder>,
            namingStrategy: PropertyNamingStrategy) {
        if (myType.isEnumType && findFormatShape() != CirJsonFormat.Shape.OBJECT) {
            return
        }

        val properties = propertiesMap.values.toTypedArray()
        propertiesMap.clear()

        for (prop in properties) {
            var property = prop
            val fullName = property.fullName

            val rename = if (!property.isExplicitlyNamed ||
                    myConfig.isEnabled(MapperFeature.ALLOW_EXPLICIT_PROPERTY_RENAMING)) {
                if (myForSerialization) {
                    if (property.hasGetter()) {
                        namingStrategy.nameForGetterMethod(myConfig, property.getter!!, fullName.simpleName)
                    } else if (property.hasField()) {
                        namingStrategy.nameForField(myConfig, property.field!!, fullName.simpleName)
                    } else {
                        null
                    }
                } else {
                    if (property.hasSetter()) {
                        namingStrategy.nameForSetterMethod(myConfig, property.internalSetterUnchecked!!,
                                fullName.simpleName)
                    } else if (property.hasConstructorParameter()) {
                        namingStrategy.nameForConstructorParameter(myConfig, property.constructorParameter!!,
                                fullName.simpleName)
                    } else if (property.hasField()) {
                        namingStrategy.nameForField(myConfig, property.internalFieldUnchecked!!, fullName.simpleName)
                    } else if (property.hasGetter()) {
                        namingStrategy.nameForGetterMethod(myConfig, property.internalGetterUnchecked!!,
                                fullName.simpleName)
                    } else {
                        null
                    }
                }
            } else {
                null
            }

            val simpleName = if (rename != null && !fullName.hasSimpleName(rename)) {
                property = property.withSimpleName(rename)
                rename
            } else {
                fullName.simpleName
            }

            val old = propertiesMap[simpleName]

            if (old == null) {
                propertiesMap[simpleName] = property
            } else {
                old.addAll(property)
            }

            replaceCreatorProperty(myCreatorProperties, property)
        }
    }

    /**
     * Helper method called to check if given property should be renamed using [PropertyNamingStrategies].
     * 
     * NOTE: copied+simplified version of `BasicBeanDescription.findExpectedFormat()`.
     */
    private fun findFormatShape(): CirJsonFormat.Shape? {
        val defaultFormat = myConfig.getDefaultPropertyFormat(myClassDefinition.rawType)

        if (defaultFormat.hasShape()) {
            return defaultFormat.shape
        }

        return myAnnotationIntrospector.findFormat(myConfig, myClassDefinition)?.shape
    }

    protected open fun renameWithWrappers(properties: MutableMap<String, POJOPropertyBuilder>) {
        val iterator = properties.iterator()
        var renamed: LinkedList<POJOPropertyBuilder>? = null

        while (iterator.hasNext()) {
            val entry = iterator.next()
            var property = entry.value
            val member = property.primaryMember ?: continue
            val wrapperName = myAnnotationIntrospector.findWrapperName(myConfig, member) ?: continue

            if (!wrapperName.hasSimpleName()) {
                continue
            }

            if (wrapperName == property.fullName) {
                continue
            }

            if (renamed == null) {
                renamed = LinkedList()
            }

            property = property.withName(wrapperName)
            renamed.add(property)
            iterator.remove()
        }

        renamed ?: return

        for (property in renamed) {
            val name = property.name
            val old = properties[name]

            if (old == null) {
                properties[name] = property
            } else {
                old.addAll(property)
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Internal methods: sorting
     *******************************************************************************************************************
     */

    protected open fun sortProperties(properties: MutableMap<String, POJOPropertyBuilder>) {
        val introspector = myAnnotationIntrospector
        val alpha = introspector.findSerializationSortAlphabetically(myConfig, myClassDefinition)
        val sortAlpha = alpha ?: myConfig.shouldSortPropertiesAlphabetically()
        val indexed = anyIndexed(properties.values)

        val propertyOrder = introspector.findSerializationPropertyOrder(myConfig, myClassDefinition)

        if (!sortAlpha && !indexed && myCreatorProperties == null && propertyOrder == null) {
            return
        }

        val size = properties.size
        val all = if (sortAlpha) {
            TreeMap<String, POJOPropertyBuilder>()
        } else {
            LinkedHashMap(size + size)
        }

        for (property in properties.values) {
            all[property.name] = property
        }

        val ordered = LinkedHashMap<String, POJOPropertyBuilder>(size + size)

        if (propertyOrder != null) {
            for (n in propertyOrder) {
                var name = n
                var w = all.remove(name)

                if (w == null) {
                    for (property in properties.values) {
                        if (name != property.internalName) {
                            continue
                        }

                        w = property
                        name = property.name
                        break
                    }
                }

                if (w != null) {
                    ordered[name] = w
                }
            }
        }

        if (indexed) {
            val byIndex = TreeMap<Int, POJOPropertyBuilder>()
            val iterator = all.iterator()

            while (iterator.hasNext()) {
                val entry = iterator.next()
                val property = entry.value
                val index = property.metadata.index ?: continue
                byIndex[index] = property
                iterator.remove()
            }

            for (property in properties.values) {
                ordered[property.name] = property
            }
        }

        if (myCreatorProperties != null &&
                (!sortAlpha || myConfig.isEnabled(MapperFeature.SORT_CREATOR_PROPERTIES_FIRST))) {
            val creators = if (sortAlpha) {
                val sorted = TreeMap<String, POJOPropertyBuilder?>()

                for (property in myCreatorProperties!!) {
                    sorted[property?.name ?: continue] = property
                }

                sorted.values
            } else {
                myCreatorProperties!!
            }

            for (property in creators) {
                val name = property?.name ?: continue

                if (name in all) {
                    ordered[name] = property
                }
            }
        }

        ordered.putAll(all)
        properties.clear()
        properties.putAll(ordered)
    }

    private fun anyIndexed(properties: Collection<POJOPropertyBuilder>): Boolean {
        return properties.any { it.metadata.hasIndex() }
    }

    /*
     *******************************************************************************************************************
     * Internal methods: conflict resolution
     *******************************************************************************************************************
     */

    /**
     * Method that will be given a [MutableList] with 2 or more accessors that may be in conflict: it will need to
     * remove lower-priority accessors to leave just a single highest-priority accessor to use. If this succeeds method
     * returns `true`, otherwise `false`.
     * 
     * NOTE: method will directly modify given `MutableList` directly, regardless of whether it ultimately succeeds or
     * not.
     *
     * @return `true` if seeming conflict was resolved and there only remains single accessor
     */
    protected open fun resolveFieldVsGetter(accessors: MutableList<AnnotatedMember>): Boolean {
        do {
            val accessor1 = accessors[0]
            val accessor2 = accessors[1]

            if (accessor1 is AnnotatedField) {
                if (accessor2 is AnnotatedMethod) {
                    accessors.removeAt(0)
                    continue
                }
            } else if (accessor1 is AnnotatedMethod) {
                if (accessor2 is AnnotatedField) {
                    accessors.removeAt(1)
                    continue
                }
            }

            return false
        } while (accessors.isNotEmpty())

        return true
    }

    /*
     *******************************************************************************************************************
     * Internal methods: helpers
     *******************************************************************************************************************
     */

    protected open fun reportProblem(message: String): Nothing {
        throw IllegalArgumentException("Problem with definition of $myClassDefinition: $message")
    }

    protected open fun property(properties: MutableMap<String, POJOPropertyBuilder>,
            name: PropertyName): POJOPropertyBuilder {
        val simpleName = name.simpleName
        return properties.getOrPut(simpleName) {
            POJOPropertyBuilder(myConfig, myAnnotationIntrospector, myForSerialization, name)
        }
    }

    protected open fun property(properties: MutableMap<String, POJOPropertyBuilder>,
            implicitName: String): POJOPropertyBuilder {
        return properties.getOrPut(implicitName) {
            POJOPropertyBuilder(myConfig, myAnnotationIntrospector, myForSerialization,
                    PropertyName.construct(implicitName))
        }
    }

    private fun findNamingStrategy(): PropertyNamingStrategy? {
        val namingDef = myAnnotationIntrospector.findNamingStrategy(myConfig, myClassDefinition)
                ?: return myConfig.propertyNamingStrategy

        if (namingDef is PropertyNamingStrategy) {
            return namingDef
        }

        if (namingDef !is KClass<*>) {
            reportProblem(
                    "AnnotationIntrospector returned PropertyNamingStrategy definition of type ${namingDef.className}; expected type `PropertyNamingStrategy` or `KClass<PropertyNamingStrategy>` instead")
        }

        if (namingDef == PropertyNamingStrategy::class) {
            return null
        }

        if (!PropertyNamingStrategy::class.isAssignableFrom(namingDef)) {
            reportProblem(
                    "AnnotationIntrospector returned KClass ${namingDef.className}; expected `KClass<PropertyNamingStrategy>`")
        }

        return myConfig.handlerInstantiator?.namingStrategyInstance(myConfig, myClassDefinition, namingDef)
                ?: namingDef.createInstance(myConfig.canOverrideAccessModifiers()) as PropertyNamingStrategy
    }

    protected open fun replaceCreatorProperty(creatorProperties: MutableList<POJOPropertyBuilder?>?,
            property: POJOPropertyBuilder): Boolean {
        creatorProperties ?: return false
        val constructorParameter = property.constructorParameter


        for (i in creatorProperties.indices) {
            val creatorProperty = creatorProperties[i] ?: continue

            if (creatorProperty.constructorParameter === constructorParameter) {
                return true
            }
        }

        return false
    }

    companion object {

        internal fun create(config: MapperConfig<*>, forSerialization: Boolean, type: KotlinType,
                classDefinition: AnnotatedClass, accessorNaming: AccessorNamingStrategy): POJOPropertiesCollector {
            return POJOPropertiesCollector(config, forSerialization, type, classDefinition, accessorNaming)
        }

    }

}