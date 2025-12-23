package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.annotations.ObjectIdResolver
import org.cirjson.cirjackson.databind.cirjsontype.PolymorphicTypeValidator
import org.cirjson.cirjackson.databind.configuration.DatatypeFeature
import org.cirjson.cirjackson.databind.configuration.DatatypeFeatures
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.Annotated
import org.cirjson.cirjackson.databind.introspection.AnnotatedClass
import org.cirjson.cirjackson.databind.introspection.ClassIntrospector
import org.cirjson.cirjackson.databind.introspection.ObjectIdInfo
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.*
import java.lang.reflect.Type
import java.util.*
import kotlin.reflect.KClass

/**
 * Shared base class for [DeserializationContext] and [SerializerProvider], context objects passed through data-binding
 * process. Designed so that some of the implementations can rely on shared aspects like access to secondary contextual
 * objects like type factories or handler instantiators.
 */
abstract class DatabindContext {

    /*
     *******************************************************************************************************************
     * Generic config access
     *******************************************************************************************************************
     */

    /**
     * Accessor to currently active configuration (both per-request configs and per-mapper config).
     */
    abstract val config: MapperConfig<*>

    /**
     * Convenience accessor for accessing serialization view in use (if any); equivalent to:
     * ```
     * config.annotationIntrospector
     * ```
     */
    abstract val annotationIntrospector: AnnotationIntrospector?

    /*
     *******************************************************************************************************************
     * Access to specific config settings
     *******************************************************************************************************************
     */

    /**
     * Convenience method for checking whether specified Mapper feature is enabled or not. Shortcut for:
     * ```
     * config.isEnabled(feature)
     * ```
     */
    abstract fun isEnabled(feature: MapperFeature): Boolean

    val isAnnotationProcessingEnabled: Boolean
        get() = isEnabled(MapperFeature.USE_ANNOTATIONS)

    /**
     * Method for checking whether specified datatype feature is enabled or not.
     */
    abstract fun isEnabled(feature: DatatypeFeature)

    abstract val datatypeFeatures: DatatypeFeatures

    /**
     * Convenience method for determining whether it is ok to try to force override of access modifiers; equivalent to:
     * ```
     * config.canOverrideAccessModifiers()
     * ```
     */
    abstract fun canOverrideAccessModifiers(): Boolean

    /**
     * Accessor for locating currently active view, if any; returns `null` if no view has been set.
     */
    abstract val activeView: KClass<*>?

    abstract val locale: Locale

    abstract val timeZone: TimeZone

    abstract fun getDefaultPropertyFormat(baseType: KClass<*>): CirJsonFormat.Value

    /*
     *******************************************************************************************************************
     * Generic attributes
     *******************************************************************************************************************
     */

    /**
     * Method for accessing attributes available in this context. Per-call attributes have the highest precedence;
     * attributes set via [ObjectReader] or [ObjectWriter] have lower precedence.
     *
     * @param key Key of the attribute to get
     * 
     * @return Value of the attribute, if any; `null` otherwise
     */
    abstract fun getAttribute(key: Any): Any?

    /**
     * Method for setting per-call value of given attribute. This will override any previously defined value for the
     * attribute within this context.
     *
     * @param key Key of the attribute to set
     * 
     * @param value Value to set attribute to
     *
     * @return This context object, to allow chaining
     */
    abstract fun setAttribute(key: Any, value: Any?): DatabindContext

    /*
     *******************************************************************************************************************
     * Type instantiation/resolution
     *******************************************************************************************************************
     */

    /**
     * Convenience method for constructing [KotlinType] for given JDK type (usually [Class])
     */
    open fun constructType(type: Type?): KotlinType? {
        return type?.let { typeFactory.constructType(it) }
    }

    /**
     * Convenience method for constructing [KotlinType] for given [KClass]
     */
    open fun constructType(type: KClass<*>?): KotlinType? {
        return constructType(type?.java)
    }

    /**
     * Convenience method for constructing subtypes, retaining generic type parameter (if any).
     */
    abstract fun constructSpecializedType(baseType: KotlinType, subclass: KClass<*>): KotlinType

    /**
     * Lookup method called when code needs to resolve class name from input; usually simple lookup. Note that unlike
     * [resolveAndValidateSubType] this method DOES NOT validate subtype against configured [PolymorphicTypeValidator]:
     * usually because such check has already been made.
     */
    open fun resolveSubType(baseType: KotlinType, subClassName: String): KotlinType? {
        if (subClassName.indexOf('<') > 0) {
            val type = typeFactory.constructFromCanonical(subClassName)

            if (type.isTypeOrSubTypeOf(baseType.rawClass)) {
                return type
            }
        } else {
            val clazz = try {
                typeFactory.findClass(subClassName)
            } catch (_: ClassNotFoundException) {
                return null
            } catch (e: Exception) {
                throw invalidTypeIdException(baseType, subClassName,
                        "problem: (${e::class.qualifiedName}) ${e.exceptionMessage()}")
            }

            if (baseType.isTypeOrSuperTypeOf(clazz)) {
                return typeFactory.constructSpecializedType(baseType, clazz)
            }
        }

        throw invalidTypeIdException(baseType, subClassName, "Not a subtype")
    }

    /**
     * Lookup method similar to [resolveSubType], but one that also validates that resulting subtype is valid according
     * to given [PolymorphicTypeValidator].
     */
    open fun resolveAndValidateSubType(baseType: KotlinType, subClass: String,
            polymorphicTypeValidator: PolymorphicTypeValidator): KotlinType? {
        val ltIndex = subClass.indexOf('<')

        if (ltIndex > 0) {
            return resolveAndValidateGeneric(baseType, subClass, polymorphicTypeValidator, ltIndex)
        }

        val validity = polymorphicTypeValidator.validateSubClassName(this, baseType, subClass)

        if (validity == PolymorphicTypeValidator.Validity.DENIED) {
            return throwSubtypeNameNotAllowed(baseType, subClass, polymorphicTypeValidator)
        }

        val clazz = try {
            typeFactory.findClass(subClass)
        } catch (_: ClassNotFoundException) {
            return null
        } catch (e: Exception) {
            throw invalidTypeIdException(baseType, subClass,
                    "problem: (${e::class.qualifiedName}) ${e.exceptionMessage()}")
        }

        if (!baseType.isTypeOrSuperTypeOf(clazz)) {
            return throwNotASubtype(baseType, subClass)
        }

        val subtype = typeFactory.constructSpecializedType(baseType, clazz)

        if (validity != PolymorphicTypeValidator.Validity.ALLOWED &&
                polymorphicTypeValidator.validateSubtype(this, baseType, subtype) !=
                PolymorphicTypeValidator.Validity.ALLOWED) {
            return throwSubtypeClassNotAllowed(baseType, subClass, polymorphicTypeValidator)
        }

        return subtype
    }

    private fun resolveAndValidateGeneric(baseType: KotlinType, subClass: String,
            polymorphicTypeValidator: PolymorphicTypeValidator, ltIndex: Int): KotlinType {
        val validity = polymorphicTypeValidator.validateSubClassName(this, baseType, subClass.substring(0, ltIndex))

        if (validity == PolymorphicTypeValidator.Validity.DENIED) {
            return throwSubtypeNameNotAllowed(baseType, subClass, polymorphicTypeValidator)
        }

        val subtype = typeFactory.constructFromCanonical(subClass)

        if (!subtype.isTypeOrSubTypeOf(baseType.rawClass)) {
            return throwNotASubtype(baseType, subClass)
        }

        if (validity != PolymorphicTypeValidator.Validity.ALLOWED &&
                polymorphicTypeValidator.validateSubtype(this, baseType, subtype) !=
                PolymorphicTypeValidator.Validity.ALLOWED) {
            return throwSubtypeClassNotAllowed(baseType, subClass, polymorphicTypeValidator)
        }

        return subtype
    }

    @Throws(DatabindException::class)
    protected open fun <T> throwNotASubtype(baseType: KotlinType, subType: String): T {
        throw invalidTypeIdException(baseType, subType, "Not a subtype")
    }

    @Throws(DatabindException::class)
    protected open fun <T> throwSubtypeNameNotAllowed(baseType: KotlinType, subType: String,
            polymorphicTypeValidator: PolymorphicTypeValidator): T {
        throw invalidTypeIdException(baseType, subType,
                "Configured `PolymorphicTypeValidator` (of type ${polymorphicTypeValidator.className}) denied resolution")
    }

    @Throws(DatabindException::class)
    protected open fun <T> throwSubtypeClassNotAllowed(baseType: KotlinType, subType: String,
            polymorphicTypeValidator: PolymorphicTypeValidator): T {
        throw invalidTypeIdException(baseType, subType,
                "Configured `PolymorphicTypeValidator` (of type ${polymorphicTypeValidator.className}) denied resolution")
    }

    /**
     * Helper method for constructing exception to indicate that given type id could not be resolved to a valid subtype
     * of specified base type. Most commonly called during polymorphic deserialization.
     * 
     * Note that most of the time this method should NOT be called directly: instead, method `handleUnknownTypeId()`
     * should be called which will call this method if necessary.
     */
    protected abstract fun invalidTypeIdException(baseType: KotlinType, typeId: String,
            extraDescription: String): DatabindException

    abstract val typeFactory: TypeFactory

    /*
     *******************************************************************************************************************
     *  Annotation, BeanDescription introspection
     *******************************************************************************************************************
     */

    /**
     * Convenience method for doing full "for serialization" introspection of specified type; results may be cached
     * during lifespan of this context as well.
     */
    abstract fun introspectBeanDescription(type: KotlinType): BeanDescription

    open fun introspectClassAnnotations(type: KotlinType): AnnotatedClass {
        return classIntrospector().introspectClassAnnotations(type)
    }

    open fun introspectDirectClassAnnotations(type: KotlinType): AnnotatedClass {
        return classIntrospector().introspectDirectClassAnnotations(type)
    }

    open fun introspectClassAnnotations(rawType: KClass<*>): AnnotatedClass {
        return introspectClassAnnotations(constructType(rawType)!!)
    }

    abstract fun classIntrospector(): ClassIntrospector

    /*
     *******************************************************************************************************************
     * Helper object construction
     *******************************************************************************************************************
     */

    open fun objectIdGeneratorInstance(annotated: Annotated, objectIdInfo: ObjectIdInfo): ObjectIdGenerator<*> {
        val implementationClass = objectIdInfo.generatorType!!
        val config = config
        val handlerInstantiator = config.handlerInstantiator
        val generator = handlerInstantiator?.objectIdGeneratorInstance(config, annotated, implementationClass)
                ?: implementationClass.createInstance(config.canOverrideAccessModifiers())!!
        return generator.forScope(objectIdInfo.scope!!)
    }

    open fun objectIdResolverInstance(annotated: Annotated, objectIdInfo: ObjectIdInfo): ObjectIdResolver {
        val implementationClass = objectIdInfo.resolverType
        val config = config
        val handlerInstantiator = config.handlerInstantiator
        return handlerInstantiator?.resolverIdGeneratorInstance(config, annotated, implementationClass)
                ?: implementationClass.createInstance(config.canOverrideAccessModifiers())!!
    }

    @Suppress("UNCHECKED_CAST")
    open fun converterInstance(annotated: Annotated, converterDefinition: Any?): Converter<Any, Any>? {
        converterDefinition ?: return null

        if (converterDefinition is Converter<*, *>) {
            return converterDefinition as Converter<Any, Any>
        }

        if (converterDefinition !is KClass<*>) {
            throw IllegalStateException(
                    "AnnotationIntrospector returned Converter definition of type ${converterDefinition.className}; expected type Converter or KClass<Converter> instead")
        }

        if (converterDefinition == Converter.None::class || converterDefinition.isBogusClass) {
            return null
        }

        if (!Converter::class.isAssignableFrom(converterDefinition)) {
            throw IllegalStateException(
                    "AnnotationIntrospector returned KClass ${converterDefinition.qualifiedName}; expected KClass<Converter>")
        }

        val config = config
        val handlerInstantiator = config.handlerInstantiator
        return (handlerInstantiator?.converterInstance(config, annotated, converterDefinition)
                ?: converterDefinition.createInstance(config.canOverrideAccessModifiers())) as Converter<Any, Any>
    }

    /*
     *******************************************************************************************************************
     * Miscellaneous config access
     *******************************************************************************************************************
     */

    abstract fun findRootName(rootType: KotlinType): PropertyName

    abstract fun findRootName(rawRootType: KClass<*>): PropertyName

    /*
     *******************************************************************************************************************
     * Error reporting
     *******************************************************************************************************************
     */

    /**
     * Helper method called to indicate a generic problem that stems from type definition(s), not input data, or
     * input/output state; typically this means throwing an
     * [org.cirjson.cirjackson.databind.exception.InvalidDefinitionException].
     */
    @Throws(DatabindException::class)
    abstract fun <T> reportBadDefinition(type: KotlinType, message: String?): T

    @Throws(DatabindException::class)
    open fun <T> reportBadDefinition(type: KClass<*>, message: String?): T {
        return reportBadDefinition(constructType(type)!!, message)
    }

    @Throws(DatabindException::class)
    abstract fun <T> reportBadTypeDefinition(bean: BeanDescription, message: String?): T

    /*
     *******************************************************************************************************************
     * Helper methods
     *******************************************************************************************************************
     */

    protected fun truncate(description: String?): String {
        description ?: return ""

        return description.takeIf { it.length <= MAX_ERROR_STRENGTH_LENGTH } ?: "${
            description.substring(0, MAX_ERROR_STRENGTH_LENGTH)
        }]...[${description.substring(description.length - MAX_ERROR_STRENGTH_LENGTH)}"
    }

    protected fun quotedString(description: String?): String {
        description ?: return "[N/A]"
        return "\"${truncate(description)}\""
    }

    protected fun colonConcat(messageBase: String, extra: String?): String {
        return extra?.let { "$messageBase: $it" } ?: messageBase
    }

    protected fun desc(description: String?): String {
        description ?: return "[N/A]"
        return truncate(description)
    }

    companion object {

        /**
         * Let's limit length of error messages, for cases where underlying data may be very large -- no point in
         * spamming logs with megabytes of meaningless data.
         */
        private const val MAX_ERROR_STRENGTH_LENGTH = 500

    }

}