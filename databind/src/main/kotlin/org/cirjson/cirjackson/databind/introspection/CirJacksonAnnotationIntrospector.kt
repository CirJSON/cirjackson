package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.*
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJsonAppend
import org.cirjson.cirjackson.databind.annotation.CirJsonDeserialize
import org.cirjson.cirjackson.databind.annotation.CirJsonPOJOBuilder
import org.cirjson.cirjackson.databind.annotation.CirJsonSerialize
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.configuration.PackageVersion
import org.cirjson.cirjackson.databind.external.beans.JavaBeansAnnotations
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.serialization.VirtualBeanPropertyWriter
import org.cirjson.cirjackson.databind.serialization.implementation.AttributePropertyWriter
import org.cirjson.cirjackson.databind.util.*
import java.lang.reflect.MalformedParametersException
import kotlin.reflect.KClass

/**
 * [AnnotationIntrospector] implementation that handles standard CirJackson annotations.
 */
open class CirJacksonAnnotationIntrospector : AnnotationIntrospector() {

    /**
     * Since introspection of annotation types is a performance issue in some use cases (rare, but do exist), let's try
     * a simple cache to reduce need for actual meta-annotation introspection.
     *
     * Non-final only because it needs to be recreated after deserialization.
     */
    @Transient
    protected var myAnnotationsInside: LookupCache<String, Boolean>? = SimpleLookupCache(48, 96)

    /*
     *******************************************************************************************************************
     * Local configuration settings
     *******************************************************************************************************************
     */

    /**
     * See [withConstructorPropertiesImpliesCreator] for explanation.
     *
     * Defaults to `true`.
     */
    protected var myConfigConstructorPropertiesImpliesCreator = true

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    override fun version(): Version {
        return PackageVersion.VERSION
    }

    /*
     *******************************************************************************************************************
     * Configuration
     *******************************************************************************************************************
     */

    /**
     * Method for changing behavior of [java.beans.ConstructorProperties]: if set to `true`, existence DOES indicate
     * that the given constructor should be considered a creator; `false`, that it should NOT be considered a creator
     * without explicit use of `CirJsonCreator` annotation.
     *
     * Default setting is `true`
     */
    open fun withConstructorPropertiesImpliesCreator(b: Boolean): CirJacksonAnnotationIntrospector {
        myConfigConstructorPropertiesImpliesCreator = b
        return this
    }

    /*
     *******************************************************************************************************************
     * General annotation properties
     *******************************************************************************************************************
     */

    override fun isAnnotationBundle(annotation: Annotation): Boolean {
        return super.isAnnotationBundle(annotation)
    }

    /*
     *******************************************************************************************************************
     * General annotations
     *******************************************************************************************************************
     */

    override fun findEnumValues(config: MapperConfig<*>, annotatedClass: AnnotatedClass, enumValues: Array<Enum<*>>,
            names: Array<String?>): Array<String?> {
        return super.findEnumValues(config, annotatedClass, enumValues, names)
    }

    override fun findEnumAliases(config: MapperConfig<*>, annotatedClass: AnnotatedClass, enumValues: Array<Enum<*>>,
            aliases: Array<Array<String>?>) {
        super.findEnumAliases(config, annotatedClass, enumValues, aliases)
    }

    override fun findDefaultEnumValue(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            enumValues: Array<Enum<*>>): Enum<*>? {
        return super.findDefaultEnumValue(config, annotatedClass, enumValues)
    }

    /*
     *******************************************************************************************************************
     * General class annotations
     *******************************************************************************************************************
     */

    override fun findRootName(config: MapperConfig<*>, annotatedClass: AnnotatedClass): PropertyName? {
        return super.findRootName(config, annotatedClass)
    }

    override fun isIgnorableType(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Boolean? {
        return super.isIgnorableType(config, annotatedClass)
    }

    override fun findPropertyIgnoralByName(config: MapperConfig<*>,
            annotated: Annotated): CirJsonIgnoreProperties.Value? {
        return super.findPropertyIgnoralByName(config, annotated)
    }

    override fun findPropertyInclusionByName(config: MapperConfig<*>,
            annotated: Annotated): CirJsonIncludeProperties.Value? {
        return super.findPropertyInclusionByName(config, annotated)
    }

    override fun findFilterId(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findFilterId(config, annotated)
    }

    override fun findNamingStrategy(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        return super.findNamingStrategy(config, annotatedClass)
    }

    override fun findEnumNamingStrategy(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        return super.findEnumNamingStrategy(config, annotatedClass)
    }

    override fun findClassDescription(config: MapperConfig<*>, annotatedClass: AnnotatedClass): String? {
        return super.findClassDescription(config, annotatedClass)
    }

    /*
     *******************************************************************************************************************
     * Property autodetection
     *******************************************************************************************************************
     */

    override fun findAutoDetectVisibility(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            checker: VisibilityChecker): VisibilityChecker {
        return super.findAutoDetectVisibility(config, annotatedClass, checker)
    }

    /*
     *******************************************************************************************************************
     * General member (field, method/constructor) annotations
     *******************************************************************************************************************
     */

    override fun findImplicitPropertyName(config: MapperConfig<*>, member: AnnotatedMember): String? {
        return super.findImplicitPropertyName(config, member)
    }

    protected open fun findImplicitName(method: AnnotatedWithParams, index: Int): String? {
        try {
            val parameters = method.nativeParameters
            val parameter = parameters[index]

            if (parameter.isNamePresent) {
                return parameter.name
            }
        } catch (_: MalformedParametersException) {
        }

        return null
    }

    override fun findPropertyAliases(config: MapperConfig<*>, annotated: Annotated): List<PropertyName>? {
        return super.findPropertyAliases(config, annotated)
    }

    override fun hasIgnoreMarker(config: MapperConfig<*>, member: AnnotatedMember): Boolean {
        return super.hasIgnoreMarker(config, member)
    }

    override fun hasRequiredMarker(config: MapperConfig<*>, member: AnnotatedMember): Boolean? {
        return super.hasRequiredMarker(config, member)
    }

    override fun findPropertyAccess(config: MapperConfig<*>, annotated: Annotated): CirJsonProperty.Access? {
        return super.findPropertyAccess(config, annotated)
    }

    override fun findPropertyDescription(config: MapperConfig<*>, annotated: Annotated): String? {
        return super.findPropertyDescription(config, annotated)
    }

    override fun findPropertyIndex(config: MapperConfig<*>, annotated: Annotated): Int? {
        return super.findPropertyIndex(config, annotated)
    }

    override fun findPropertyDefaultValue(config: MapperConfig<*>, annotated: Annotated): String? {
        return super.findPropertyDefaultValue(config, annotated)
    }

    override fun findFormat(config: MapperConfig<*>, annotated: Annotated): CirJsonFormat.Value? {
        return super.findFormat(config, annotated)
    }

    override fun findReferenceType(config: MapperConfig<*>, member: AnnotatedMember): ReferenceProperty? {
        return super.findReferenceType(config, member)
    }

    override fun findUnwrappingNameTransformer(config: MapperConfig<*>, member: AnnotatedMember): NameTransformer? {
        return super.findUnwrappingNameTransformer(config, member)
    }

    override fun findInjectableValue(config: MapperConfig<*>, member: AnnotatedMember): CirJacksonInject.Value? {
        return super.findInjectableValue(config, member)
    }

    override fun findViews(config: MapperConfig<*>, annotated: Annotated): Array<KClass<*>>? {
        return super.findViews(config, annotated)
    }

    override fun resolveSetterConflict(config: MapperConfig<*>, setter1: AnnotatedMethod,
            setter2: AnnotatedMethod): AnnotatedMethod? {
        return super.resolveSetterConflict(config, setter1, setter2)
    }

    override fun findRenameByField(config: MapperConfig<*>, field: AnnotatedField,
            implicitName: PropertyName): PropertyName? {
        return super.findRenameByField(config, field, implicitName)
    }

    /*
     *******************************************************************************************************************
     * Annotations for Polymorphic Type handling
     *******************************************************************************************************************
     */

    override fun findPolymorphicTypeInfo(config: MapperConfig<*>, annotated: Annotated): CirJsonTypeInfo.Value? {
        return super.findPolymorphicTypeInfo(config, annotated)
    }

    override fun findTypeResolverBuilder(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findTypeResolverBuilder(config, annotated)
    }

    override fun findTypeIdResolver(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findTypeIdResolver(config, annotated)
    }

    override fun findSubtypes(config: MapperConfig<*>, annotated: Annotated): List<NamedType>? {
        return super.findSubtypes(config, annotated)
    }

    private fun findSubtypesCheckRepeatedNames(annotatedTypeName: String,
            types: Array<CirJsonSubTypes.Type>): List<NamedType> {
        val result = ArrayList<NamedType>(types.size)
        val seenNames = HashSet<String>()

        for (type in types) {
            val typeName = type.name

            if (typeName.isNotEmpty() && typeName in seenNames) {
                throw IllegalArgumentException(
                        "Annotated type [$annotatedTypeName] got repeated subtype name [$typeName]")
            }

            seenNames.add(typeName)
            result.add(NamedType(type.value, typeName))

            for (altName in type.names) {
                if (altName.isNotEmpty() && altName in seenNames) {
                    throw IllegalArgumentException(
                            "Annotated type [$annotatedTypeName] got repeated subtype name [$altName]")
                }

                seenNames.add(altName)
                result.add(NamedType(type.value, altName))
            }
        }

        return result
    }

    override fun findTypeName(config: MapperConfig<*>, annotatedClass: AnnotatedClass): String? {
        return super.findTypeName(config, annotatedClass)
    }

    override fun isTypeId(config: MapperConfig<*>, member: AnnotatedMember): Boolean? {
        return super.isTypeId(config, member)
    }

    /*
     *******************************************************************************************************************
     * Annotations for Object ID handling
     *******************************************************************************************************************
     */

    override fun findObjectIdInfo(config: MapperConfig<*>, annotated: Annotated): ObjectIdInfo? {
        return super.findObjectIdInfo(config, annotated)
    }

    override fun findObjectReferenceInfo(config: MapperConfig<*>, annotated: Annotated,
            objectIdInfo: ObjectIdInfo?): ObjectIdInfo? {
        return super.findObjectReferenceInfo(config, annotated, objectIdInfo)
    }

    /*
     *******************************************************************************************************************
     * Serialization: general annotations
     *******************************************************************************************************************
     */

    override fun findSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findSerializer(config, annotated)
    }

    override fun findKeySerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findKeySerializer(config, annotated)
    }

    override fun findContentSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findContentSerializer(config, annotated)
    }

    override fun findNullSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findNullSerializer(config, annotated)
    }

    override fun findPropertyInclusion(config: MapperConfig<*>, annotated: Annotated): CirJsonInclude.Value? {
        return super.findPropertyInclusion(config, annotated)
    }

    override fun findSerializationTyping(config: MapperConfig<*>, annotated: Annotated): CirJsonSerialize.Typing? {
        return super.findSerializationTyping(config, annotated)
    }

    override fun findSerializationConverter(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findSerializationConverter(config, annotated)
    }

    override fun findSerializationContentConverter(config: MapperConfig<*>, annotatedMember: AnnotatedMember): Any? {
        return super.findSerializationContentConverter(config, annotatedMember)
    }

    /*
     *******************************************************************************************************************
     * Serialization: type refinements
     *******************************************************************************************************************
     */

    override fun refineSerializationType(config: MapperConfig<*>, annotated: Annotated,
            baseType: KotlinType): KotlinType {
        return super.refineSerializationType(config, annotated, baseType)
    }

    /*
     *******************************************************************************************************************
     * Serialization: class annotations
     *******************************************************************************************************************
     */

    override fun findSerializationPropertyOrder(config: MapperConfig<*>,
            annotatedClass: AnnotatedClass): Array<String>? {
        return super.findSerializationPropertyOrder(config, annotatedClass)
    }

    override fun findSerializationSortAlphabetically(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return super.findSerializationSortAlphabetically(config, annotated)
    }

    private fun findSortAlpha(annotated: Annotated): Boolean? {
        val order = findAnnotation(annotated, CirJsonPropertyOrder::class)
        return order?.alphabetic?.takeIf { it }
    }

    override fun findAndAddVirtualProperties(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            properties: MutableList<BeanPropertyWriter>) {
        super.findAndAddVirtualProperties(config, annotatedClass, properties)
    }

    protected open fun constructVirtualProperty(attribute: CirJsonAppend.Attribute, config: MapperConfig<*>,
            annotatedClass: AnnotatedClass, type: KotlinType): BeanPropertyWriter {
        val metadata = if (attribute.required) {
            PropertyMetadata.STANDARD_REQUIRED
        } else {
            PropertyMetadata.STANDARD_OPTIONAL
        }

        val attributeName = attribute.value

        var propertyName = propertyName(attribute.propertyName, attribute.propertyNamespace)

        if (!propertyName.hasSimpleName()) {
            propertyName = PropertyName.construct(attributeName)
        }

        val member = VirtualAnnotatedMember(annotatedClass, annotatedClass.rawType, attributeName, type)
        val propertyDefinition =
                SimpleBeanPropertyDefinition.construct(config, member, propertyName, metadata, attribute.include)
        return AttributePropertyWriter.construct(attributeName, propertyDefinition, annotatedClass.annotations, type)
    }

    protected open fun constructVirtualProperty(property: CirJsonAppend.Property, config: MapperConfig<*>,
            annotatedClass: AnnotatedClass): BeanPropertyWriter {
        val metadata = if (property.required) {
            PropertyMetadata.STANDARD_REQUIRED
        } else {
            PropertyMetadata.STANDARD_OPTIONAL
        }

        val propertyName = propertyName(property.name, property.namespace)
        val type = config.constructType(property.type)
        val member = VirtualAnnotatedMember(annotatedClass, annotatedClass.rawType, propertyName.simpleName, type)
        val propertyDefinition =
                SimpleBeanPropertyDefinition.construct(config, member, propertyName, metadata, property.include)

        val implementationClass = property.value

        val handlerInstantiator = config.handlerInstantiator
        val beanPropertyWriter = handlerInstantiator?.virtualPropertyWriterInstance(config, implementationClass)
                ?: implementationClass.createInstance(config.canOverrideAccessModifiers()) as VirtualBeanPropertyWriter
        return beanPropertyWriter.withConfig(config, annotatedClass, propertyDefinition, type)
    }

    /*
     *******************************************************************************************************************
     * Serialization: property annotations
     *******************************************************************************************************************
     */

    override fun findNameForSerialization(config: MapperConfig<*>, annotated: Annotated): PropertyName? {
        return super.findNameForSerialization(config, annotated)
    }

    override fun hasAsKey(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return super.hasAsKey(config, annotated)
    }

    override fun hasAsValue(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return super.hasAsValue(config, annotated)
    }

    override fun hasAnyGetter(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return super.hasAnyGetter(config, annotated)
    }

    /*
     *******************************************************************************************************************
     * Deserialization: general annotations
     *******************************************************************************************************************
     */

    override fun findDeserializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findDeserializer(config, annotated)
    }

    override fun findKeyDeserializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findKeyDeserializer(config, annotated)
    }

    override fun findContentDeserializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findContentDeserializer(config, annotated)
    }

    override fun findDeserializationConverter(config: MapperConfig<*>, annotated: Annotated): Any? {
        return super.findDeserializationConverter(config, annotated)
    }

    override fun findDeserializationContentConverter(config: MapperConfig<*>, annotatedMember: AnnotatedMember): Any? {
        return super.findDeserializationContentConverter(config, annotatedMember)
    }

    /*
     *******************************************************************************************************************
     * Deserialization: type modifications
     *******************************************************************************************************************
     */

    override fun refineDeserializationType(config: MapperConfig<*>, annotated: Annotated,
            baseType: KotlinType): KotlinType {
        return super.refineDeserializationType(config, annotated, baseType)
    }

    /*
     *******************************************************************************************************************
     * Deserialization: class annotations
     *******************************************************************************************************************
     */

    override fun findValueInstantiator(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        return super.findValueInstantiator(config, annotatedClass)
    }

    override fun findPOJOBuilder(config: MapperConfig<*>, annotatedClass: AnnotatedClass): KClass<*>? {
        return super.findPOJOBuilder(config, annotatedClass)
    }

    override fun findPOJOBuilderConfig(config: MapperConfig<*>,
            annotatedClass: AnnotatedClass): CirJsonPOJOBuilder.Value? {
        return super.findPOJOBuilderConfig(config, annotatedClass)
    }

    /*
     *******************************************************************************************************************
     * Deserialization: property annotations
     *******************************************************************************************************************
     */

    override fun findNameForDeserialization(config: MapperConfig<*>, annotated: Annotated): PropertyName? {
        return super.findNameForDeserialization(config, annotated)
    }

    override fun hasAnySetter(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return super.hasAnySetter(config, annotated)
    }

    override fun findSetterInfo(config: MapperConfig<*>, annotated: Annotated): CirJsonSetter.Value? {
        return super.findSetterInfo(config, annotated)
    }

    override fun findMergeInfo(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return super.findMergeInfo(config, annotated)
    }

    override fun findCreatorAnnotation(config: MapperConfig<*>, annotated: Annotated): CirJsonCreator.Mode? {
        return super.findCreatorAnnotation(config, annotated)
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    protected open fun isIgnorable(annotated: Annotated): Boolean {
        val annotation = findAnnotation(annotated, CirJsonIgnore::class)

        if (annotation != null) {
            return annotation.value
        }

        return BEANS_HELPER?.findTransient(annotated) ?: false
    }

    protected open fun classIfExplicit(clazz: KClass<*>?): KClass<*>? {
        if (clazz == null || clazz.isBogusClass) {
            return null
        }

        return clazz
    }

    protected open fun classIfExplicit(clazz: KClass<*>?, implicit: KClass<*>): KClass<*>? {
        val realClazz = classIfExplicit(clazz) ?: return null
        return realClazz.takeUnless { it == implicit }
    }

    protected open fun propertyName(localName: String, namespace: String?): PropertyName {
        if (localName.isEmpty()) {
            return PropertyName.USE_DEFAULT
        }

        if (namespace.isNullOrEmpty()) {
            return PropertyName.construct(localName)
        }

        return PropertyName.construct(localName, namespace)
    }

    private fun primitiveAndWrapper(baseType: KClass<*>, refinement: KClass<*>): Boolean {
        if (baseType.isPrimitive) {
            return baseType == refinement.java.primitiveType()
        }

        if (refinement.isPrimitive) {
            return refinement == baseType.java.primitiveType()
        }

        return false
    }

    private fun primitiveAndWrapper(baseType: KotlinType, refinement: KClass<*>): Boolean {
        if (baseType.isPrimitive) {
            val primitive = refinement.java.primitiveType()
            return baseType.hasRawClass(primitive!!.kotlin)
        }

        if (refinement.isPrimitive) {
            return refinement == baseType.rawClass.java.primitiveType()
        }

        return false
    }

    private fun databindException(message: String): DatabindException {
        return DatabindException.from(null as CirJsonParser?, message)
    }

    private fun databindException(throwable: Throwable, message: String): DatabindException {
        return DatabindException.from(null as CirJsonParser?, message, throwable)
    }

    companion object {

        private val ANNOTATIONS_TO_INFER_SERIALIZATION =
                arrayOf(CirJsonSerialize::class, CirJsonView::class, CirJsonFormat::class, CirJsonTypeInfo::class,
                        CirJsonRawValue::class, CirJsonUnwrapped::class, CirJsonBackReference::class,
                        CirJsonManagedReference::class)

        private val ANNOTATIONS_TO_INFER_DESERIALIZATION =
                arrayOf(CirJsonDeserialize::class, CirJsonView::class, CirJsonFormat::class, CirJsonTypeInfo::class,
                        CirJsonUnwrapped::class, CirJsonBackReference::class, CirJsonManagedReference::class,
                        CirJsonMerge::class)

        private val BEANS_HELPER = try {
            JavaBeansAnnotations.IMPLEMENTATION
        } catch (t: Throwable) {
            t.rethrowIfFatal()
            null
        }

    }

}