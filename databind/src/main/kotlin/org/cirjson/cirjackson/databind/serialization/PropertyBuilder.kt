package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.annotation.CirJsonSerialize
import org.cirjson.cirjackson.databind.cirjsontype.TypeSerializer
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.BeanPropertyDefinition
import org.cirjson.cirjackson.databind.util.*
import kotlin.reflect.KClass

/**
 * Helper class for [BeanSerializerFactory] that is used to construct [BeanPropertyWriter] instances. Can be subclassed
 * to change behavior.
 */
open class PropertyBuilder(protected val myConfig: SerializationConfig,
        protected val myBeanDescription: BeanDescription) {

    protected val myAnnotationIntrospector = myConfig.annotationIntrospector!!

    /**
     * If a property has serialization inclusion value of [CirJsonInclude.Include.NON_DEFAULT], we may need to know the
     * default value of the bean, to know if property value equals default one.
     * 
     * NOTE: only used if enclosing class defines `NON_DEFAULT`, but NOT if it is the global default OR per-property
     * override.
     */
    protected var myDefaultBean: Any? = null

    /**
     * Default inclusion mode for properties of the POJO for which properties are collected; possibly overridden on
     * per-property basis. Combines global inclusion defaults and per-type (annotation and type-override) inclusion
     * overrides.
     */
    protected val myDefaultInclusion: CirJsonInclude.Value

    /**
     * Marker flag used to indicate that "real" default values are to be used for properties, as per per-type value
     * inclusion of type [CirJsonInclude.Include.NON_DEFAULT]
     */
    protected val myUseRealPropertyDefaults: Boolean

    init {
        val inclusionPerType =
                CirJsonInclude.Value.merge(myBeanDescription.findPropertyInclusion(CirJsonInclude.Value.EMPTY),
                        myConfig.getDefaultPropertyInclusion(myBeanDescription.beanClass, CirJsonInclude.Value.EMPTY))!!
        myDefaultInclusion = CirJsonInclude.Value.merge(myConfig.defaultPropertyInclusion, inclusionPerType)!!
        myUseRealPropertyDefaults = inclusionPerType.valueInclusion == CirJsonInclude.Include.NON_DEFAULT
    }

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    open val classAnnotations: Annotations
        get() = myBeanDescription.classAnnotations

    /**
     * @param contentTypeSerializer Optional explicit type information serializer to use for contained values (only used
     * for properties that are of container type)
     */
    protected open fun buildWriter(context: SerializerProvider, propertyDefinition: BeanPropertyDefinition?,
            declaredType: KotlinType, serializer: ValueSerializer<*>?, typeSerializer: TypeSerializer?,
            contentTypeSerializer: TypeSerializer?, annotatedMember: AnnotatedMember,
            defaultUseStaticTyping: Boolean): BeanPropertyWriter {
        var serializationType = try {
            findSerializationType(annotatedMember, defaultUseStaticTyping, declaredType)
        } catch (e: DatabindException) {
            if (propertyDefinition == null) {
                return context.reportBadDefinition(declaredType, e.exceptionMessage())
            }

            return context.reportBadPropertyDefinition(myBeanDescription, propertyDefinition, e.exceptionMessage())
        }

        if (contentTypeSerializer != null) {
            if (serializationType == null) {
                serializationType = declaredType
            }

            serializationType.contentType ?: return context.reportBadPropertyDefinition(myBeanDescription,
                    propertyDefinition, "serialization type $serializationType has no content")
            serializationType = serializationType.withContentTypeHandler(contentTypeSerializer)
        }

        var valueToSuppress: Any? = null
        var suppressNulls = false

        val actualType = serializationType ?: declaredType

        val accessor = propertyDefinition!!.accessor ?: return context.reportBadPropertyDefinition(myBeanDescription,
                propertyDefinition, "could not determine property type")
        val rawPropertyType = accessor.rawType

        var includeValue = myConfig.getDefaultInclusion(actualType.rawClass, rawPropertyType, myDefaultInclusion)
        includeValue = includeValue.withOverrides(propertyDefinition.findInclusion())

        val inclusion = includeValue.valueInclusion.takeUnless { it == CirJsonInclude.Include.USE_DEFAULTS }
                ?: CirJsonInclude.Include.ALWAYS

        when (inclusion) {
            CirJsonInclude.Include.NON_DEFAULT -> {
                val defaultBean = defaultBean

                if (myUseRealPropertyDefaults && defaultBean != null) {
                    if (context.isEnabled(MapperFeature.CAN_OVERRIDE_ACCESS_MODIFIERS)) {
                        annotatedMember.fixAccess(myConfig.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
                    }

                    try {
                        valueToSuppress = annotatedMember.getValue(defaultBean)
                    } catch (e: Exception) {
                        return throwWrapped(e, propertyDefinition.name, defaultBean)
                    }
                } else {
                    valueToSuppress = BeanUtil.getDefaultValue(actualType)
                    suppressNulls = true
                }

                if (valueToSuppress == null) {
                    suppressNulls = true
                    valueToSuppress = BeanPropertyWriter.MARKER_FOR_EMPTY
                } else {
                    if (valueToSuppress::class.isArray) {
                        valueToSuppress = ArrayBuilders.getArrayComparator(valueToSuppress)
                    }
                }
            }

            CirJsonInclude.Include.NON_ABSENT -> {
                suppressNulls = true

                if (actualType.isReferenceType) {
                    valueToSuppress = BeanPropertyWriter.MARKER_FOR_EMPTY
                }
            }

            CirJsonInclude.Include.NON_EMPTY -> {
                suppressNulls = true
                valueToSuppress = BeanPropertyWriter.MARKER_FOR_EMPTY
            }

            CirJsonInclude.Include.NON_NULL -> {
                suppressNulls = true
            }

            else -> {
                val emptyCirJsonArrays = SerializationFeature.WRITE_EMPTY_CIRJSON_ARRAYS

                if (actualType.isContainerType && !myConfig.isEnabled(emptyCirJsonArrays)) {
                    valueToSuppress = BeanPropertyWriter.MARKER_FOR_EMPTY
                }
            }
        }

        val views = propertyDefinition.findViews() ?: myBeanDescription.findDefaultViews()
        var beanPropertyWriter =
                constructPropertyWriter(propertyDefinition, annotatedMember, myBeanDescription.classAnnotations,
                        declaredType, serializer, typeSerializer, serializationType, suppressNulls, valueToSuppress,
                        views)

        val serializerDefinition = myAnnotationIntrospector.findNullSerializer(myConfig, annotatedMember)

        if (serializerDefinition != null) {
            beanPropertyWriter.assignNullSerializer(context.serializerInstance(annotatedMember, serializerDefinition))
        }

        val unwrapper = myAnnotationIntrospector.findUnwrappingNameTransformer(myConfig, annotatedMember)

        if (unwrapper != null) {
            beanPropertyWriter = beanPropertyWriter.unwrappingWriter(unwrapper)
        }

        return beanPropertyWriter
    }

    /**
     * Overridable factory method for actual construction of [BeanPropertyWriter]; often needed if subclassing
     * [buildWriter] method.
     */
    protected open fun constructPropertyWriter(propertyDefinition: BeanPropertyDefinition, member: AnnotatedMember?,
            contextAnnotations: Annotations?, declaredType: KotlinType?, serializer: ValueSerializer<*>?,
            typeSerializer: TypeSerializer?, serializationType: KotlinType?, suppressNulls: Boolean,
            suppressableValue: Any?, includeInViews: Array<KClass<*>>?): BeanPropertyWriter {
        return BeanPropertyWriter.construct(propertyDefinition, member, contextAnnotations, declaredType, serializer,
                typeSerializer, serializationType, suppressNulls, suppressableValue, includeInViews)
    }

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    /**
     * Method that will try to determine statically defined type of property being serialized, based on annotations (for
     * overrides), and alternatively declared type (if static typing for serialization is enabled). If neither can be
     * used (no annotations, dynamic typing), returns null.
     */
    protected open fun findSerializationType(annotated: Annotated, useStaticTyping: Boolean,
            declaredType: KotlinType): KotlinType? {
        var realUseStaticTyping = useStaticTyping
        var realDeclaredType = declaredType

        val secondary = myAnnotationIntrospector.refineSerializationType(myConfig, annotated, realDeclaredType)

        if (secondary !== realDeclaredType) {
            val serializationClass = secondary.rawClass
            val rawDeclared = declaredType.rawClass

            if (!serializationClass.isAssignableFrom(rawDeclared) && !rawDeclared.isAssignableFrom(
                            serializationClass)) {
                throw IllegalArgumentException(
                        "Illegal concrete-type annotation for method '${annotated.name}': class ${serializationClass.qualifiedName} not a supertype of (declared) class ${rawDeclared.qualifiedName}")
            }

            realUseStaticTyping = true
            realDeclaredType = secondary
        }

        val typing = myAnnotationIntrospector.findSerializationTyping(myConfig, annotated)

        if (typing != null && typing != CirJsonSerialize.Typing.DEFAULT_TYPING) {
            realUseStaticTyping = typing == CirJsonSerialize.Typing.STATIC
        }

        if (!realUseStaticTyping) {
            return null
        }

        return realDeclaredType.withStaticTyping()
    }

    protected open val defaultBean: Any?
        get() {
            var defaultBean = myDefaultBean

            if (defaultBean == null) {
                defaultBean =
                        myBeanDescription.instantiateBean(myConfig.canOverrideAccessModifiers()) ?: NO_DEFAULT_MARKER
                myDefaultBean = defaultBean
            }

            return myDefaultBean.takeUnless { defaultBean === NO_DEFAULT_MARKER }
        }

    @Suppress("ThrowableNotThrown")
    protected open fun <T> throwWrapped(e: Exception, propertyName: String, defaultBean: Any): T {
        val throwable = e.cause ?: e
        throwable.throwIfError().throwIfRuntimeException()
        throw IllegalArgumentException(
                "Failed to get property '$propertyName' of default ${defaultBean::class.qualifiedName} instance")
    }

    companion object {

        private val NO_DEFAULT_MARKER: Any = false

    }

}