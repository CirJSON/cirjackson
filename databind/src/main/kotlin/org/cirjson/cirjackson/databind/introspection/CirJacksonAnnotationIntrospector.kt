package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.*
import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.*
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.configuration.PackageVersion
import org.cirjson.cirjackson.databind.external.beans.JavaBeansAnnotations
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.serialization.VirtualBeanPropertyWriter
import org.cirjson.cirjackson.databind.serialization.cirjackson.RawSerializer
import org.cirjson.cirjackson.databind.serialization.implementation.AttributePropertyWriter
import org.cirjson.cirjackson.databind.type.MapLikeType
import org.cirjson.cirjackson.databind.type.TypeFactory
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

    /**
     * Annotations with meta-annotation [CirJacksonAnnotationsInside] are considered bundles.
     */
    override fun isAnnotationBundle(annotation: Annotation): Boolean {
        val type = annotation.annotationClass
        val typeName = type.qualifiedName!!
        var b = myAnnotationsInside!![typeName]

        if (b == null) {
            b = type.annotations.any { it.annotationClass == type }
            myAnnotationsInside!!.setIfAbsent(typeName, b)
        }

        return b
    }

    /*
     *******************************************************************************************************************
     * General annotations
     *******************************************************************************************************************
     */

    override fun findEnumValues(config: MapperConfig<*>, annotatedClass: AnnotatedClass, enumValues: Array<Enum<*>>,
            names: Array<String?>): Array<String?> {
        val enumToPropertyMap = LinkedHashMap<String, String>()

        for (field in annotatedClass.fields()) {
            val property = field.getAnnotation(CirJsonProperty::class) ?: continue
            val propertyValue = property.value

            if (propertyValue.isNotEmpty()) {
                enumToPropertyMap[field.name] = propertyValue
            }
        }

        for (i in enumValues.indices) {
            val defaultName = enumValues[i].name
            val explicitValue = enumToPropertyMap[defaultName] ?: continue
            names[i] = explicitValue
        }

        return names
    }

    override fun findEnumAliases(config: MapperConfig<*>, annotatedClass: AnnotatedClass, enumValues: Array<Enum<*>>,
            aliases: Array<Array<String>?>) {
        val enumToAliasMap = HashMap<String, Array<String>>()

        for (field in annotatedClass.fields()) {
            val alias = field.getAnnotation(CirJsonAlias::class) ?: continue
            enumToAliasMap.putIfAbsent(field.name, alias.value.map { it }.toTypedArray())
        }

        for (i in enumValues.indices) {
            val enumValue = enumValues[i]
            aliases[i] = enumToAliasMap.getOrDefault(enumValue.name, emptyArray())
        }
    }

    /**
     * Finds the Enum value that should be considered the default value, if possible.
     *
     * This implementation relies on [CirJsonEnumDefaultValue] annotation to determine the default value if present.
     *
     * @param config The configuration of the mapper
     *
     * @param annotatedClass The Enum class to scan for the default value annotation.
     *
     * @param enumValues The Enum values of the Enum class.
     *
     * @return `null` if none found, or it's not possible to determine one.
     */
    override fun findDefaultEnumValue(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            enumValues: Array<Enum<*>>): Enum<*>? {
        for (field in annotatedClass.fields()) {
            if (!field.type.isEnumType) {
                continue
            }

            findAnnotation(field, CirJsonEnumDefaultValue::class) ?: continue

            for (enumValue in enumValues) {
                if (enumValue.name == field.name) {
                    return enumValue
                }
            }
        }

        return null
    }

    /*
     *******************************************************************************************************************
     * General class annotations
     *******************************************************************************************************************
     */

    override fun findRootName(config: MapperConfig<*>, annotatedClass: AnnotatedClass): PropertyName? {
        val annotation = findAnnotation(annotatedClass, CirJsonRootName::class) ?: return null
        val namespace = annotation.namespace.takeUnless { it.isEmpty() }
        return PropertyName.construct(annotation.value, namespace)
    }

    override fun isIgnorableType(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Boolean? {
        val ignore = findAnnotation(annotatedClass, CirJsonIgnore::class)
        return ignore?.value
    }

    override fun findPropertyIgnoralByName(config: MapperConfig<*>,
            annotated: Annotated): CirJsonIgnoreProperties.Value? {
        val value =
                findAnnotation(annotated, CirJsonIgnoreProperties::class) ?: return CirJsonIgnoreProperties.Value.EMPTY
        return CirJsonIgnoreProperties.Value.from(value)
    }

    override fun findPropertyInclusionByName(config: MapperConfig<*>,
            annotated: Annotated): CirJsonIncludeProperties.Value? {
        val value =
                findAnnotation(annotated, CirJsonIncludeProperties::class) ?: return CirJsonIncludeProperties.Value.ALL
        return CirJsonIncludeProperties.Value.from(value)
    }

    override fun findFilterId(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonFilter::class) ?: return null
        return annotation.value.takeUnless { it.isEmpty() }
    }

    override fun findNamingStrategy(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        val annotation = findAnnotation(annotatedClass, CirJsonNaming::class)
        return annotation?.value
    }

    override fun findEnumNamingStrategy(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        val annotation = findAnnotation(annotatedClass, EnumNaming::class)
        return annotation?.value
    }

    override fun findClassDescription(config: MapperConfig<*>, annotatedClass: AnnotatedClass): String? {
        val annotation = findAnnotation(annotatedClass, CirJsonClassDescription::class)
        return annotation?.value
    }

    /*
     *******************************************************************************************************************
     * Property autodetection
     *******************************************************************************************************************
     */

    override fun findAutoDetectVisibility(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            checker: VisibilityChecker): VisibilityChecker {
        val annotation = findAnnotation(annotatedClass, CirJsonAutoDetect::class) ?: return checker
        return checker.withOverrides(CirJsonAutoDetect.Value.from(annotation))
    }

    /*
     *******************************************************************************************************************
     * General member (field, method/constructor) annotations
     *******************************************************************************************************************
     */

    override fun findImplicitPropertyName(config: MapperConfig<*>, member: AnnotatedMember): String? {
        if (member is AnnotatedField) {
            return member.name
        }

        if (member !is AnnotatedParameter) {
            return null
        }

        val owner = member.owner

        if (owner is AnnotatedConstructor) {
            if (BEANS_HELPER != null) {
                val name = BEANS_HELPER.findConstructorName(member)

                if (name != null) {
                    return name.simpleName
                }
            }

            return findImplicitName(owner, member.index)
        }

        if (owner !is AnnotatedMethod || !owner.isStatic) {
            return null
        }

        return findImplicitName(owner, member.index)
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
        val annotation = findAnnotation(annotated, CirJsonAlias::class) ?: return null
        val strings = annotation.value
        val length = strings.size

        if (length == 0) {
            return emptyList()
        }

        val result = ArrayList<PropertyName>(length)

        for (string in strings) {
            result.add(PropertyName.construct(string))
        }

        return result
    }

    override fun hasIgnoreMarker(config: MapperConfig<*>, member: AnnotatedMember): Boolean {
        return isIgnorable(member)
    }

    override fun hasRequiredMarker(config: MapperConfig<*>, member: AnnotatedMember): Boolean? {
        val annotation = findAnnotation(member, CirJsonProperty::class)
        return annotation?.required
    }

    override fun findPropertyAccess(config: MapperConfig<*>, annotated: Annotated): CirJsonProperty.Access? {
        val annotation = findAnnotation(annotated, CirJsonProperty::class)
        return annotation?.access
    }

    override fun findPropertyDescription(config: MapperConfig<*>, annotated: Annotated): String? {
        val description = findAnnotation(annotated, CirJsonPropertyDescription::class)
        return description?.value
    }

    override fun findPropertyIndex(config: MapperConfig<*>, annotated: Annotated): Int? {
        val property = findAnnotation(annotated, CirJsonProperty::class) ?: return null
        return property.index.takeUnless { it == CirJsonProperty.INDEX_UNKNOWN }
    }

    override fun findPropertyDefaultValue(config: MapperConfig<*>, annotated: Annotated): String? {
        val property = findAnnotation(annotated, CirJsonProperty::class) ?: return null
        return property.defaultValue.takeUnless { it.isEmpty() }
    }

    override fun findFormat(config: MapperConfig<*>, annotated: Annotated): CirJsonFormat.Value? {
        val format = findAnnotation(annotated, CirJsonFormat::class) ?: return null
        return CirJsonFormat.Value.from(format)
    }

    override fun findReferenceType(config: MapperConfig<*>, member: AnnotatedMember): ReferenceProperty? {
        val reference1 = findAnnotation(member, CirJsonManagedReference::class)

        if (reference1 != null) {
            return ReferenceProperty.managed(reference1.value)
        }

        val reference2 = findAnnotation(member, CirJsonBackReference::class) ?: return null
        return ReferenceProperty.back(reference2.value)
    }

    override fun findUnwrappingNameTransformer(config: MapperConfig<*>, member: AnnotatedMember): NameTransformer? {
        val annotation = findAnnotation(member, CirJsonUnwrapped::class) ?: return null

        if (!annotation.enabled) {
            return null
        }

        val prefix = annotation.prefix
        val suffix = annotation.suffix
        return NameTransformer.simpleTransformer(prefix, suffix)
    }

    override fun findInjectableValue(config: MapperConfig<*>, member: AnnotatedMember): CirJacksonInject.Value? {
        val annotation = findAnnotation(member, CirJacksonInject::class) ?: return null
        val value = CirJacksonInject.Value.from(annotation)

        if (value.isIdNotNull) {
            return value
        }

        val id = if (member !is AnnotatedMethod) {
            member.rawType.qualifiedName!!
        } else if (member.parameterCount == 0) {
            member.rawType.qualifiedName!!
        } else {
            member.getRawParameterType(0)!!.qualifiedName!!
        }

        return value.withId(id)
    }

    override fun findViews(config: MapperConfig<*>, annotated: Annotated): Array<KClass<*>>? {
        val annotation = findAnnotation(annotated, CirJsonView::class)
        return annotation?.value
    }

    /**
     * Specific implementation that will use following tie-breaker on given setter parameter types:
     *
     * * If either one is primitive type then either return `null` (both primitives) or one that is primitive (when only
     * primitive)
     *
     * * If only one is of type `String`, return that setter
     *
     * * Otherwise return `null`
     *
     * Returning `null` will indicate that resolution could not be done.
     */
    override fun resolveSetterConflict(config: MapperConfig<*>, setter1: AnnotatedMethod,
            setter2: AnnotatedMethod): AnnotatedMethod? {
        val class1 = setter1.getRawParameterType(0)!!
        val class2 = setter2.getRawParameterType(0)!!

        return if (class1.isPrimitive) {
            setter1.takeUnless { class2.isPrimitive }
        } else if (class2.isPrimitive) {
            setter2
        } else if (class1 == String::class) {
            setter1.takeUnless { class2 == String::class }
        } else {
            setter2.takeIf { class2 == String::class }
        }
    }

    /*
     *******************************************************************************************************************
     * Annotations for Polymorphic Type handling
     *******************************************************************************************************************
     */

    override fun findPolymorphicTypeInfo(config: MapperConfig<*>, annotated: Annotated): CirJsonTypeInfo.Value? {
        val typeInfo = findAnnotation(annotated, CirJsonTypeInfo::class) ?: return null
        return CirJsonTypeInfo.Value.from(typeInfo)
    }

    override fun findTypeResolverBuilder(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonTypeResolver::class)
        return annotation?.value
    }

    override fun findTypeIdResolver(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonTypeIdResolver::class)
        return annotation?.value
    }

    override fun findSubtypes(config: MapperConfig<*>, annotated: Annotated): List<NamedType>? {
        val subTypes = findAnnotation(annotated, CirJsonSubTypes::class) ?: return null
        val types = subTypes.value

        if (subTypes.failOnRepeatedNames) {
            return findSubtypesCheckRepeatedNames(annotated.name, types)
        }

        val result = ArrayList<NamedType>(types.size)

        for (type in types) {
            result.add(NamedType(type.value, type.name))

            for (name in type.names) {
                result.add(NamedType(type.value, name))
            }
        }

        return result
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
        val annotation = findAnnotation(annotatedClass, CirJsonTypeName::class)
        return annotation?.value
    }

    override fun isTypeId(config: MapperConfig<*>, member: AnnotatedMember): Boolean? {
        return hasAnnotation(member, CirJsonTypeId::class)
    }

    /*
     *******************************************************************************************************************
     * Annotations for Object ID handling
     *******************************************************************************************************************
     */

    override fun findObjectIdInfo(config: MapperConfig<*>, annotated: Annotated): ObjectIdInfo? {
        val info = findAnnotation(annotated, CirJsonIdentityInfo::class) ?: return null

        if (info.generator == ObjectIdGenerators.None::class) {
            return null
        }

        val name = PropertyName.construct(info.property)
        return ObjectIdInfo(name, info.scope, info.generator, info.resolver)
    }

    override fun findObjectReferenceInfo(config: MapperConfig<*>, annotated: Annotated,
            objectIdInfo: ObjectIdInfo?): ObjectIdInfo? {
        val reference = findAnnotation(annotated, CirJsonIdentityReference::class) ?: return objectIdInfo
        return (objectIdInfo ?: ObjectIdInfo.EMPTY).withAlwaysAsId(reference.alwaysAsID)
    }

    /*
     *******************************************************************************************************************
     * Serialization: general annotations
     *******************************************************************************************************************
     */

    override fun findSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonSerialize::class)

        if (annotation != null) {
            val serializerClass = annotation.using

            if (serializerClass != ValueSerializer.None::class) {
                return serializerClass
            }
        }

        val annotationRaw = findAnnotation(annotated, CirJsonRawValue::class) ?: return null

        if (!annotationRaw.value) {
            return null
        }

        val clazz = annotated.rawType
        return RawSerializer<Any?>(clazz)
    }

    override fun findKeySerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonSerialize::class) ?: return null
        return annotation.keyUsing.takeUnless { it == ValueSerializer.None::class }
    }

    override fun findContentSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonSerialize::class) ?: return null
        return annotation.contentUsing.takeUnless { it == ValueSerializer.None::class }
    }

    override fun findNullSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonSerialize::class) ?: return null
        return annotation.nullsUsing.takeUnless { it == ValueSerializer.None::class }
    }

    override fun findPropertyInclusion(config: MapperConfig<*>, annotated: Annotated): CirJsonInclude.Value? {
        val include = findAnnotation(annotated, CirJsonInclude::class) ?: return CirJsonInclude.Value.EMPTY
        return CirJsonInclude.Value.from(include)
    }

    override fun findSerializationTyping(config: MapperConfig<*>, annotated: Annotated): CirJsonSerialize.Typing? {
        val annotation = findAnnotation(annotated, CirJsonSerialize::class)
        return annotation?.typing
    }

    override fun findSerializationConverter(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonSerialize::class) ?: return null
        return classIfExplicit(annotation.converter, Converter.None::class)
    }

    override fun findSerializationContentConverter(config: MapperConfig<*>, annotatedMember: AnnotatedMember): Any? {
        val annotation = findAnnotation(annotatedMember, CirJsonSerialize::class) ?: return null
        return classIfExplicit(annotation.contentConverter, Converter.None::class)
    }

    /*
     *******************************************************************************************************************
     * Serialization: type refinements
     *******************************************************************************************************************
     */

    override fun refineSerializationType(config: MapperConfig<*>, annotated: Annotated,
            baseType: KotlinType): KotlinType {
        var type = baseType
        val typeFactory = config.typeFactory

        val cirJsonSerialize = findAnnotation(annotated, CirJsonSerialize::class)

        val serializeClass = cirJsonSerialize?.let { classIfExplicit(it.valueAs) }

        if (serializeClass != null) {
            type = findSpecificType(annotated, typeFactory, type, type, serializeClass)
        }

        if (type.isMapLikeType) {
            var keyType = type.keyType
            val keyClass = cirJsonSerialize?.let { classIfExplicit(it.keyAs) }

            if (keyClass != null) {
                keyType = findSpecificType(annotated, typeFactory, type, keyType!!, keyClass)

                type = (type as MapLikeType).withKeyType(keyType)
            }
        }

        var contentType = type.contentType ?: return type
        val contentClass = cirJsonSerialize?.let { classIfExplicit(it.contentAs) } ?: return type

        contentType = findSpecificType(annotated, typeFactory, type, contentType, contentClass)

        return type.withContentType(contentType)
    }

    protected open fun findSpecificType(annotated: Annotated, typeFactory: TypeFactory, mainType: KotlinType,
            type: KotlinType, typeClass: KClass<*>): KotlinType {
        return if (type.hasRawClass(typeClass)) {
            type.withStaticTyping()
        } else {
            val currentRaw = type.rawClass

            try {
                if (typeClass.isAssignableFrom(currentRaw)) {
                    typeFactory.constructGeneralizedType(type, typeClass)
                } else if (currentRaw.isAssignableFrom(typeClass)) {
                    typeFactory.constructSpecializedType(type, typeClass)
                } else if (primitiveAndWrapper(currentRaw, typeClass)) {
                    type.withStaticTyping()
                } else {
                    throw databindException(
                            "Cannot refine serialization type $type into ${typeClass.qualifiedName}; types not related")
                }
            } catch (e: IllegalArgumentException) {
                throw databindException(e,
                        "Failed to widen type $mainType with annotation (value ${typeClass.qualifiedName}), from '${annotated.name}': ${e.message}")
            }
        }
    }

    /*
     *******************************************************************************************************************
     * Serialization: class annotations
     *******************************************************************************************************************
     */

    override fun findSerializationPropertyOrder(config: MapperConfig<*>,
            annotatedClass: AnnotatedClass): Array<String>? {
        val annotation = findAnnotation(annotatedClass, CirJsonPropertyOrder::class)
        return annotation?.value
    }

    override fun findSerializationSortAlphabetically(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return findSortAlpha(annotated)
    }

    private fun findSortAlpha(annotated: Annotated): Boolean? {
        val order = findAnnotation(annotated, CirJsonPropertyOrder::class)
        return order?.alphabetic?.takeIf { it }
    }

    override fun findAndAddVirtualProperties(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            properties: MutableList<BeanPropertyWriter>) {
        val annotation = findAnnotation(annotatedClass, CirJsonAppend::class) ?: return
        val prepend = annotation.prepend
        var propertyType: KotlinType? = null

        val attributes = annotation.attributes

        for (i in attributes.indices) {
            if (propertyType == null) {
                propertyType = config.constructType(Any::class)
            }

            val beanPropertyWriter = constructVirtualProperty(attributes[i], config, annotatedClass, propertyType)

            if (prepend) {
                properties.add(i, beanPropertyWriter)
            } else {
                properties.add(beanPropertyWriter)
            }
        }

        val annotationProperties = annotation.properties

        for (i in properties.indices) {
            val beanPropertyWriter = constructVirtualProperty(annotationProperties[i], config, annotatedClass)

            if (prepend) {
                properties.add(i, beanPropertyWriter)
            } else {
                properties.add(beanPropertyWriter)
            }
        }
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
        var useDefault = false

        val cirJsonGetter = findAnnotation(annotated, CirJsonGetter::class)

        if (cirJsonGetter != null) {
            val string = cirJsonGetter.value

            if (string.isNotEmpty()) {
                return PropertyName.construct(string)
            }

            useDefault = true
        }

        val property = findAnnotation(annotated, CirJsonProperty::class)

        if (property != null) {
            val namespace = property.namespace.takeIf { it.isNotEmpty() }
            return PropertyName.construct(property.value, namespace)
        }

        if (useDefault || hasOneOf(annotated, ANNOTATIONS_TO_INFER_SERIALIZATION)) {
            return PropertyName.USE_DEFAULT
        }

        return null
    }

    override fun hasAsKey(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        val annotation = findAnnotation(annotated, CirJsonKey::class)
        return annotation?.value
    }

    override fun hasAsValue(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        val annotation = findAnnotation(annotated, CirJsonValue::class)
        return annotation?.value
    }

    override fun hasAnyGetter(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        val annotation = findAnnotation(annotated, CirJsonAnyGetter::class)
        return annotation?.isEnabled
    }

    /*
     *******************************************************************************************************************
     * Deserialization: general annotations
     *******************************************************************************************************************
     */

    override fun findDeserializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonDeserialize::class) ?: return null
        return annotation.using.takeIf { it != ValueDeserializer.None::class }
    }

    override fun findKeyDeserializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonDeserialize::class) ?: return null
        return annotation.keyUsing.takeIf { it != KeyDeserializer.None::class }
    }

    override fun findContentDeserializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonDeserialize::class) ?: return null
        return annotation.contentUsing.takeIf { it != ValueDeserializer.None::class }
    }

    override fun findDeserializationConverter(config: MapperConfig<*>, annotated: Annotated): Any? {
        val annotation = findAnnotation(annotated, CirJsonDeserialize::class) ?: return null
        return classIfExplicit(annotation.converter, Converter.None::class)
    }

    override fun findDeserializationContentConverter(config: MapperConfig<*>, annotatedMember: AnnotatedMember): Any? {
        val annotation = findAnnotation(annotatedMember, CirJsonDeserialize::class) ?: return null
        return classIfExplicit(annotation.contentConverter, Converter.None::class)
    }

    /*
     *******************************************************************************************************************
     * Deserialization: type modifications
     *******************************************************************************************************************
     */

    override fun refineDeserializationType(config: MapperConfig<*>, annotated: Annotated,
            baseType: KotlinType): KotlinType {
        var type = baseType
        val typeFactory = config.typeFactory

        val cirJsonDeserialize = findAnnotation(annotated, CirJsonDeserialize::class)

        val valueClass = cirJsonDeserialize?.let { classIfExplicit(it.valueAs) }

        if (valueClass != null && !type.hasRawClass(valueClass) && !primitiveAndWrapper(type, valueClass)) {
            try {
                type = typeFactory.constructSpecializedType(type, valueClass)
            } catch (e: IllegalArgumentException) {
                throw databindException(e,
                        "Failed to narrow type $type with annotation (value ${valueClass.qualifiedName}), from '${annotated.name}': ${e.message}")
            }
        }

        if (type.isMapLikeType) {
            var keyType = type.keyType
            val keyClass = cirJsonDeserialize?.let { classIfExplicit(it.keyAs) }

            if (keyClass != null && !primitiveAndWrapper(keyType!!, keyClass)) {
                try {
                    keyType = typeFactory.constructSpecializedType(keyType, keyClass)
                } catch (e: IllegalArgumentException) {
                    throw databindException(e,
                            "Failed to narrow type $type with annotation (value ${keyClass.qualifiedName}), from '${annotated.name}': ${e.message}")
                }

                type = (type as MapLikeType).withKeyType(keyType)
            }
        }

        var contentType = type.contentType ?: return type
        val contentClass = cirJsonDeserialize?.let { classIfExplicit(it.keyAs) } ?: return type

        try {
            contentType = typeFactory.constructSpecializedType(contentType, contentClass)
        } catch (e: IllegalArgumentException) {
            throw databindException(e,
                    "Failed to narrow type $type with annotation (value ${contentClass.qualifiedName}), from '${annotated.name}': ${e.message}")
        }

        return type.withContentType(contentType)
    }

    /*
     *******************************************************************************************************************
     * Deserialization: class annotations
     *******************************************************************************************************************
     */

    override fun findValueInstantiator(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        val annotation = findAnnotation(annotatedClass, CirJsonValueInstantiator::class)
        return annotation?.value
    }

    override fun findPOJOBuilder(config: MapperConfig<*>, annotatedClass: AnnotatedClass): KClass<*>? {
        val annotation = findAnnotation(annotatedClass, CirJsonDeserialize::class) ?: return null
        return classIfExplicit(annotation.builder)
    }

    override fun findPOJOBuilderConfig(config: MapperConfig<*>,
            annotatedClass: AnnotatedClass): CirJsonPOJOBuilder.Value? {
        val annotation = findAnnotation(annotatedClass, CirJsonPOJOBuilder::class) ?: return null
        return CirJsonPOJOBuilder.Value(annotation)
    }

    /*
     *******************************************************************************************************************
     * Deserialization: property annotations
     *******************************************************************************************************************
     */

    override fun findNameForDeserialization(config: MapperConfig<*>, annotated: Annotated): PropertyName? {
        var useDefault = false

        val cirJsonSetter = findAnnotation(annotated, CirJsonSetter::class)

        if (cirJsonSetter != null) {
            val string = cirJsonSetter.value

            if (string.isNotEmpty()) {
                return PropertyName.construct(string)
            }

            useDefault = true
        }

        val property = findAnnotation(annotated, CirJsonProperty::class)

        if (property != null) {
            val namespace = property.namespace.takeIf { it.isNotEmpty() }
            return PropertyName.construct(property.value, namespace)
        }

        if (useDefault || hasOneOf(annotated, ANNOTATIONS_TO_INFER_DESERIALIZATION)) {
            return PropertyName.USE_DEFAULT
        }

        return null
    }

    override fun hasAnySetter(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        val annotation = findAnnotation(annotated, CirJsonAnySetter::class)
        return annotation?.isEnabled
    }

    override fun findSetterInfo(config: MapperConfig<*>, annotated: Annotated): CirJsonSetter.Value? {
        return CirJsonSetter.Value.from(findAnnotation(annotated, CirJsonSetter::class))
    }

    override fun findMergeInfo(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        val annotation = findAnnotation(annotated, CirJsonMerge::class) ?: return null
        return annotation.value.asBoolean()
    }

    override fun findCreatorAnnotation(config: MapperConfig<*>, annotated: Annotated): CirJsonCreator.Mode? {
        val annotation = findAnnotation(annotated, CirJsonCreator::class)

        if (annotation != null) {
            return annotation.mode
        }

        if (!myConfigConstructorPropertiesImpliesCreator ||
                !config.isEnabled(MapperFeature.INFER_CREATOR_FROM_CONSTRUCTOR_PROPERTIES)) {
            return null
        }

        val b = BEANS_HELPER?.hasCreatorAnnotation(annotated) ?: return null

        if (b) {
            return CirJsonCreator.Mode.PROPERTIES
        }

        return null
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