package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.*
import org.cirjson.cirjackson.core.Version
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJsonPOJOBuilder
import org.cirjson.cirjackson.databind.annotation.CirJsonSerialize
import org.cirjson.cirjackson.databind.cirjsontype.NamedType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.serialization.BeanPropertyWriter
import org.cirjson.cirjackson.databind.util.NameTransformer
import org.cirjson.cirjackson.databind.util.isBogusClass
import kotlin.reflect.KClass

/**
 * Helper class that allows using 2 introspectors such that one introspector acts as the primary one to use; and second
 * one as a fallback used if the primary does not provide conclusive or useful result for a method.
 *
 * An obvious consequence of priority is that it is easy to construct longer chains of introspectors by linking multiple
 * pairs. Currently most likely combination is that of using the default CirJackson provider, along with JAXB annotation
 * introspector.
 */
open class AnnotationIntrospectorPair(protected val myPrimary: AnnotationIntrospector,
        protected val mySecondary: AnnotationIntrospector) : AnnotationIntrospector() {

    override fun version(): Version {
        return myPrimary.version()
    }

    override fun allIntrospectors(): Collection<AnnotationIntrospector> {
        return allIntrospectors(ArrayList())
    }

    override fun allIntrospectors(
            result: MutableCollection<AnnotationIntrospector>): Collection<AnnotationIntrospector> {
        myPrimary.allIntrospectors(result)
        mySecondary.allIntrospectors(result)
        return result
    }

    override fun isAnnotationBundle(annotation: Annotation): Boolean {
        return myPrimary.isAnnotationBundle(annotation) || mySecondary.isAnnotationBundle(annotation)
    }

    /*
     *******************************************************************************************************************
     * General class annotations
     *******************************************************************************************************************
     */

    override fun findRootName(config: MapperConfig<*>, annotatedClass: AnnotatedClass): PropertyName? {
        return PropertyName.merge(myPrimary.findRootName(config, annotatedClass),
                mySecondary.findRootName(config, annotatedClass))
    }

    override fun findPropertyIgnoralByName(config: MapperConfig<*>,
            annotated: Annotated): CirJsonIgnoreProperties.Value? {
        val value1 = myPrimary.findPropertyIgnoralByName(config, annotated)
        val value2 = mySecondary.findPropertyIgnoralByName(config, annotated)
        return value2?.withOverrides(value1) ?: value1
    }

    override fun findPropertyInclusionByName(config: MapperConfig<*>,
            annotated: Annotated): CirJsonIncludeProperties.Value? {
        val value1 = myPrimary.findPropertyInclusionByName(config, annotated)
        val value2 = mySecondary.findPropertyInclusionByName(config, annotated)
        return value2?.withOverrides(value1) ?: value1
    }

    override fun isIgnorableType(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Boolean? {
        return myPrimary.isIgnorableType(config, annotatedClass) ?: mySecondary.isIgnorableType(config, annotatedClass)
    }

    override fun findFilterId(config: MapperConfig<*>, annotated: Annotated): Any? {
        return myPrimary.findFilterId(config, annotated) ?: mySecondary.findFilterId(config, annotated)
    }

    override fun findNamingStrategy(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        return myPrimary.findNamingStrategy(config, annotatedClass) ?: mySecondary.findNamingStrategy(config,
                annotatedClass)
    }

    override fun findEnumNamingStrategy(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        return myPrimary.findEnumNamingStrategy(config, annotatedClass) ?: mySecondary.findEnumNamingStrategy(config,
                annotatedClass)
    }

    override fun findClassDescription(config: MapperConfig<*>, annotatedClass: AnnotatedClass): String? {
        return myPrimary.findClassDescription(config, annotatedClass) ?: mySecondary.findClassDescription(config,
                annotatedClass)
    }

    /*
     *******************************************************************************************************************
     * Property autodetection
     *******************************************************************************************************************
     */

    override fun findAutoDetectVisibility(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            checker: VisibilityChecker): VisibilityChecker {
        val realChecker = mySecondary.findAutoDetectVisibility(config, annotatedClass, checker)
        return myPrimary.findAutoDetectVisibility(config, annotatedClass, realChecker)
    }

    /*
     *******************************************************************************************************************
     * Type handling
     *******************************************************************************************************************
     */

    override fun findPolymorphicTypeInfo(config: MapperConfig<*>, annotated: Annotated): CirJsonTypeInfo.Value? {
        return myPrimary.findPolymorphicTypeInfo(config, annotated) ?: mySecondary.findPolymorphicTypeInfo(config,
                annotated)
    }

    override fun findTypeResolverBuilder(config: MapperConfig<*>, annotated: Annotated): Any? {
        return myPrimary.findTypeResolverBuilder(config, annotated) ?: mySecondary.findTypeResolverBuilder(config,
                annotated)
    }

    override fun findTypeIdResolver(config: MapperConfig<*>, annotated: Annotated): Any? {
        return myPrimary.findTypeIdResolver(config, annotated) ?: mySecondary.findTypeIdResolver(config, annotated)
    }

    override fun findSubtypes(config: MapperConfig<*>, annotated: Annotated): List<NamedType>? {
        val types1 = myPrimary.findSubtypes(config, annotated)
        val types2 = mySecondary.findSubtypes(config, annotated)

        if (types1.isNullOrEmpty()) {
            return types2
        }

        if (types2.isNullOrEmpty()) {
            return types1
        }

        return types1 + types2
    }

    override fun findTypeName(config: MapperConfig<*>, annotatedClass: AnnotatedClass): String? {
        return myPrimary.findTypeName(config, annotatedClass)?.takeUnless { it.isEmpty() } ?: mySecondary.findTypeName(
                config, annotatedClass)
    }

    /*
     *******************************************************************************************************************
     * General member (field, method/constructor) annotations
     *******************************************************************************************************************
     */

    override fun findReferenceType(config: MapperConfig<*>, member: AnnotatedMember): ReferenceProperty? {
        return myPrimary.findReferenceType(config, member) ?: mySecondary.findReferenceType(config, member)
    }

    override fun findUnwrappingNameTransformer(config: MapperConfig<*>, member: AnnotatedMember): NameTransformer? {
        return myPrimary.findUnwrappingNameTransformer(config, member) ?: mySecondary.findUnwrappingNameTransformer(
                config, member)
    }

    override fun findInjectableValue(config: MapperConfig<*>, member: AnnotatedMember): CirJacksonInject.Value? {
        val result = myPrimary.findInjectableValue(config, member)

        if (result?.useInput != null) {
            return result
        }

        val secondary = mySecondary.findInjectableValue(config, member) ?: return result
        return result?.withUseInput(secondary.useInput) ?: secondary
    }

    override fun hasIgnoreMarker(config: MapperConfig<*>, member: AnnotatedMember): Boolean {
        return myPrimary.hasIgnoreMarker(config, member) || mySecondary.hasIgnoreMarker(config, member)
    }

    override fun hasRequiredMarker(config: MapperConfig<*>, member: AnnotatedMember): Boolean? {
        return myPrimary.hasRequiredMarker(config, member) ?: mySecondary.hasRequiredMarker(config, member)
    }

    /*
     *******************************************************************************************************************
     * Serialization: general annotations
     *******************************************************************************************************************
     */

    override fun findSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val potentialResult = myPrimary.findSerializer(config, annotated)

        if (isExplicitClassOrObject(potentialResult, ValueSerializer.None::class)) {
            return potentialResult
        }

        return explicitClassOrObject(mySecondary.findSerializer(config, annotated), ValueSerializer.None::class)
    }

    override fun findKeySerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val potentialResult = myPrimary.findKeySerializer(config, annotated)

        if (isExplicitClassOrObject(potentialResult, ValueSerializer.None::class)) {
            return potentialResult
        }

        return explicitClassOrObject(mySecondary.findKeySerializer(config, annotated), ValueSerializer.None::class)
    }

    override fun findContentSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val potentialResult = myPrimary.findContentSerializer(config, annotated)

        if (isExplicitClassOrObject(potentialResult, ValueSerializer.None::class)) {
            return potentialResult
        }

        return explicitClassOrObject(mySecondary.findContentSerializer(config, annotated), ValueSerializer.None::class)
    }

    override fun findNullSerializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val potentialResult = myPrimary.findNullSerializer(config, annotated)

        if (isExplicitClassOrObject(potentialResult, ValueSerializer.None::class)) {
            return potentialResult
        }

        return explicitClassOrObject(mySecondary.findNullSerializer(config, annotated), ValueSerializer.None::class)
    }

    override fun findPropertyInclusion(config: MapperConfig<*>, annotated: Annotated): CirJsonInclude.Value? {
        val value1 = myPrimary.findPropertyInclusion(config, annotated)
        val value2 = mySecondary.findPropertyInclusion(config, annotated)
        return value2?.withOverrides(value1) ?: value1
    }

    override fun findSerializationTyping(config: MapperConfig<*>, annotated: Annotated): CirJsonSerialize.Typing? {
        return myPrimary.findSerializationTyping(config, annotated) ?: mySecondary.findSerializationTyping(config,
                annotated)
    }

    override fun findSerializationConverter(config: MapperConfig<*>, annotated: Annotated): Any? {
        return myPrimary.findSerializationConverter(config, annotated) ?: mySecondary.findSerializationConverter(config,
                annotated)
    }

    override fun findSerializationContentConverter(config: MapperConfig<*>, annotatedMember: AnnotatedMember): Any? {
        return myPrimary.findSerializationContentConverter(config, annotatedMember)
                ?: mySecondary.findSerializationContentConverter(config, annotatedMember)
    }

    override fun findViews(config: MapperConfig<*>, annotated: Annotated): Array<KClass<*>>? {
        return myPrimary.findViews(config, annotated) ?: mySecondary.findViews(config, annotated)
    }

    override fun isTypeId(config: MapperConfig<*>, member: AnnotatedMember): Boolean? {
        return myPrimary.isTypeId(config, member) ?: mySecondary.isTypeId(config, member)
    }

    override fun findObjectIdInfo(config: MapperConfig<*>, annotated: Annotated): ObjectIdInfo? {
        return myPrimary.findObjectIdInfo(config, annotated) ?: mySecondary.findObjectIdInfo(config, annotated)
    }

    override fun findObjectReferenceInfo(config: MapperConfig<*>, annotated: Annotated,
            objectIdInfo: ObjectIdInfo?): ObjectIdInfo? {
        val realInfo = mySecondary.findObjectReferenceInfo(config, annotated, objectIdInfo)
        return myPrimary.findObjectReferenceInfo(config, annotated, realInfo)
    }

    override fun findFormat(config: MapperConfig<*>, annotated: Annotated): CirJsonFormat.Value? {
        val value1 = myPrimary.findFormat(config, annotated)
        val value2 = mySecondary.findFormat(config, annotated)
        return value2?.withOverrides(value1) ?: value1
    }

    override fun findWrapperName(config: MapperConfig<*>, annotated: Annotated): PropertyName? {
        return PropertyName.merge(myPrimary.findWrapperName(config, annotated),
                mySecondary.findWrapperName(config, annotated))
    }

    override fun findPropertyDefaultValue(config: MapperConfig<*>, annotated: Annotated): String? {
        return myPrimary.findPropertyDefaultValue(config, annotated) ?: mySecondary.findPropertyDefaultValue(config,
                annotated)
    }

    override fun findPropertyDescription(config: MapperConfig<*>, annotated: Annotated): String? {
        return myPrimary.findPropertyDescription(config, annotated) ?: mySecondary.findPropertyDescription(config,
                annotated)
    }

    override fun findPropertyIndex(config: MapperConfig<*>, annotated: Annotated): Int? {
        return myPrimary.findPropertyIndex(config, annotated) ?: mySecondary.findPropertyIndex(config, annotated)
    }

    override fun findImplicitPropertyName(config: MapperConfig<*>, member: AnnotatedMember): String? {
        return myPrimary.findImplicitPropertyName(config, member) ?: mySecondary.findImplicitPropertyName(config,
                member)
    }

    override fun findPropertyAliases(config: MapperConfig<*>, annotated: Annotated): List<PropertyName>? {
        return myPrimary.findPropertyAliases(config, annotated) ?: mySecondary.findPropertyAliases(config, annotated)
    }

    override fun findPropertyAccess(config: MapperConfig<*>, annotated: Annotated): CirJsonProperty.Access? {
        val access = myPrimary.findPropertyAccess(config, annotated)

        if (access != null && access != CirJsonProperty.Access.AUTO) {
            return access
        }

        return mySecondary.findPropertyAccess(config, annotated) ?: CirJsonProperty.Access.AUTO
    }

    override fun resolveSetterConflict(config: MapperConfig<*>, setter1: AnnotatedMethod,
            setter2: AnnotatedMethod): AnnotatedMethod? {
        return myPrimary.resolveSetterConflict(config, setter1, setter2) ?: mySecondary.resolveSetterConflict(config,
                setter1, setter2)
    }

    override fun findRenameByField(config: MapperConfig<*>, field: AnnotatedField,
            implicitName: PropertyName): PropertyName? {
        return PropertyName.merge(myPrimary.findRenameByField(config, field, implicitName),
                mySecondary.findRenameByField(config, field, implicitName))
    }

    /*
     *******************************************************************************************************************
     * Serialization: type refinements
     *******************************************************************************************************************
     */

    override fun refineSerializationType(config: MapperConfig<*>, annotated: Annotated,
            baseType: KotlinType): KotlinType {
        val type = mySecondary.refineSerializationType(config, annotated, baseType)
        return myPrimary.refineSerializationType(config, annotated, type)
    }

    /*
     *******************************************************************************************************************
     * Serialization: class annotations
     *******************************************************************************************************************
     */

    override fun findSerializationPropertyOrder(config: MapperConfig<*>,
            annotatedClass: AnnotatedClass): Array<String>? {
        return myPrimary.findSerializationPropertyOrder(config, annotatedClass)
                ?: mySecondary.findSerializationPropertyOrder(config, annotatedClass)
    }

    override fun findSerializationSortAlphabetically(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return myPrimary.findSerializationSortAlphabetically(config, annotated)
                ?: mySecondary.findSerializationSortAlphabetically(config, annotated)
    }

    override fun findAndAddVirtualProperties(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            properties: MutableList<BeanPropertyWriter>) {
        mySecondary.findAndAddVirtualProperties(config, annotatedClass, properties)
        myPrimary.findAndAddVirtualProperties(config, annotatedClass, properties)
    }

    /*
     *******************************************************************************************************************
     * Serialization: property annotations
     *******************************************************************************************************************
     */

    override fun findNameForSerialization(config: MapperConfig<*>, annotated: Annotated): PropertyName? {
        return PropertyName.merge(myPrimary.findNameForSerialization(config, annotated),
                mySecondary.findNameForSerialization(config, annotated))
    }

    override fun hasAsKey(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return myPrimary.hasAsKey(config, annotated) ?: mySecondary.hasAsKey(config, annotated)
    }

    override fun hasAsValue(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return myPrimary.hasAsValue(config, annotated) ?: mySecondary.hasAsValue(config, annotated)
    }

    override fun hasAnyGetter(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return myPrimary.hasAnyGetter(config, annotated) ?: mySecondary.hasAnyGetter(config, annotated)
    }

    override fun findEnumValues(config: MapperConfig<*>, annotatedClass: AnnotatedClass, enumValues: Array<Enum<*>>,
            names: Array<String?>): Array<String?> {
        val realNames = mySecondary.findEnumValues(config, annotatedClass, enumValues, names)
        return myPrimary.findEnumValues(config, annotatedClass, enumValues, realNames)
    }

    override fun findEnumAliases(config: MapperConfig<*>, annotatedClass: AnnotatedClass, enumValues: Array<Enum<*>>,
            aliases: Array<Array<String>?>) {
        mySecondary.findEnumAliases(config, annotatedClass, enumValues, aliases)
        myPrimary.findEnumAliases(config, annotatedClass, enumValues, aliases)
    }

    override fun findDefaultEnumValue(config: MapperConfig<*>, annotatedClass: AnnotatedClass,
            enumValues: Array<Enum<*>>): Enum<*>? {
        return myPrimary.findDefaultEnumValue(config, annotatedClass, enumValues) ?: mySecondary.findDefaultEnumValue(
                config, annotatedClass, enumValues)
    }

    /*
     *******************************************************************************************************************
     * Deserialization: general annotations
     *******************************************************************************************************************
     */

    override fun findDeserializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val potentialResult = myPrimary.findDeserializer(config, annotated)

        if (isExplicitClassOrObject(potentialResult, ValueSerializer.None::class)) {
            return potentialResult
        }

        return explicitClassOrObject(mySecondary.findDeserializer(config, annotated), ValueDeserializer.None::class)
    }

    override fun findKeyDeserializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val potentialResult = myPrimary.findKeyDeserializer(config, annotated)

        if (isExplicitClassOrObject(potentialResult, ValueSerializer.None::class)) {
            return potentialResult
        }

        return explicitClassOrObject(mySecondary.findKeyDeserializer(config, annotated), ValueDeserializer.None::class)
    }

    override fun findContentDeserializer(config: MapperConfig<*>, annotated: Annotated): Any? {
        val potentialResult = myPrimary.findContentDeserializer(config, annotated)

        if (isExplicitClassOrObject(potentialResult, ValueSerializer.None::class)) {
            return potentialResult
        }

        return explicitClassOrObject(mySecondary.findContentDeserializer(config, annotated),
                ValueDeserializer.None::class)
    }

    override fun findDeserializationConverter(config: MapperConfig<*>, annotated: Annotated): Any? {
        return myPrimary.findDeserializationConverter(config, annotated) ?: mySecondary.findDeserializationConverter(
                config, annotated)
    }

    override fun findDeserializationContentConverter(config: MapperConfig<*>, annotatedMember: AnnotatedMember): Any? {
        return myPrimary.findDeserializationContentConverter(config, annotatedMember)
                ?: mySecondary.findDeserializationContentConverter(config, annotatedMember)
    }

    /*
     *******************************************************************************************************************
     * Deserialization: type refinements
     *******************************************************************************************************************
     */

    override fun refineDeserializationType(config: MapperConfig<*>, annotated: Annotated,
            baseType: KotlinType): KotlinType {
        val type = mySecondary.refineDeserializationType(config, annotated, baseType)
        return myPrimary.refineDeserializationType(config, annotated, type)
    }

    /*
     *******************************************************************************************************************
     * Deserialization: class annotations
     *******************************************************************************************************************
     */

    override fun findValueInstantiator(config: MapperConfig<*>, annotatedClass: AnnotatedClass): Any? {
        return myPrimary.findValueInstantiator(config, annotatedClass) ?: mySecondary.findValueInstantiator(config,
                annotatedClass)
    }

    override fun findPOJOBuilder(config: MapperConfig<*>, annotatedClass: AnnotatedClass): KClass<*>? {
        return myPrimary.findPOJOBuilder(config, annotatedClass) ?: mySecondary.findPOJOBuilder(config, annotatedClass)
    }

    override fun findPOJOBuilderConfig(config: MapperConfig<*>,
            annotatedClass: AnnotatedClass): CirJsonPOJOBuilder.Value? {
        return myPrimary.findPOJOBuilderConfig(config, annotatedClass) ?: mySecondary.findPOJOBuilderConfig(config,
                annotatedClass)
    }

    /*
     *******************************************************************************************************************
     * Deserialization: method annotations
     *******************************************************************************************************************
     */

    override fun findNameForDeserialization(config: MapperConfig<*>, annotated: Annotated): PropertyName? {
        return PropertyName.merge(myPrimary.findNameForDeserialization(config, annotated),
                mySecondary.findNameForDeserialization(config, annotated))
    }

    override fun hasAnySetter(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return myPrimary.hasAnySetter(config, annotated) ?: mySecondary.hasAnySetter(config, annotated)
    }

    override fun findSetterInfo(config: MapperConfig<*>, annotated: Annotated): CirJsonSetter.Value? {
        val value1 = myPrimary.findSetterInfo(config, annotated)
        val value2 = mySecondary.findSetterInfo(config, annotated)
        return value2?.withOverrides(value1) ?: value1
    }

    override fun findMergeInfo(config: MapperConfig<*>, annotated: Annotated): Boolean? {
        return myPrimary.findMergeInfo(config, annotated) ?: mySecondary.findMergeInfo(config, annotated)
    }

    override fun findCreatorAnnotation(config: MapperConfig<*>, annotated: Annotated): CirJsonCreator.Mode? {
        return myPrimary.findCreatorAnnotation(config, annotated) ?: mySecondary.findCreatorAnnotation(config,
                annotated)
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    protected open fun isExplicitClassOrObject(maybeClass: Any?, implicit: KClass<*>): Boolean {
        if (maybeClass == null || maybeClass == implicit) {
            return false
        }

        if (maybeClass is KClass<*>) {
            return !maybeClass.isBogusClass
        }

        return true
    }

    protected open fun explicitClassOrObject(maybeClass: Any?, implicit: KClass<*>): Any? {
        if (maybeClass == null || maybeClass == implicit) {
            return null
        }

        if (maybeClass is KClass<*> && maybeClass.isBogusClass) {
            return null
        }

        return maybeClass
    }

    companion object {

        /**
         * Helper method for constructing a Pair from two given introspectors (if neither is `null`); or returning
         * non-`null` introspector if one is `null` (and return just `null` if both are `null`)
         */
        fun create(primary: AnnotationIntrospector?, secondary: AnnotationIntrospector?): AnnotationIntrospector? {
            primary ?: return secondary
            secondary ?: return primary
            return AnnotationIntrospectorPair(primary, secondary)
        }

    }

}