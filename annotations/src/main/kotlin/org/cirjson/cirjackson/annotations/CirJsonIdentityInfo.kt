package org.cirjson.cirjackson.annotations

import kotlin.reflect.KClass

/**
 * Annotation used for indicating that values of annotated type or property should be serializing so that instances
 * either contain additional object identifier (in addition actual object properties), or as a reference that consists
 * of an object id that refers to a full serialization. In practice this is done by serializing the first instance as
 * full object and object identity, and other references to the object as reference values.
 *
 * There are two main approaches to generating object identifier: either using a generator (either one of standard ones,
 * or a custom generator), or using a value of a property. The latter case is indicated by using a placeholder generator
 * marker [ObjectIdGenerators.PropertyGenerator]; former by using explicit generator. Object id has to be serialized as
 * a property in case of POJOs; object identity is currently NOT support for CirJSON Array types (arrays or Lists) or
 * Map types.
 *
 * Finally, note that generator type of [ObjectIdGenerators.None] indicates that no Object ID should be included or
 * used: it is included to allow suppressing Object Ids using mix-in annotations.
 *
 * @property generator Generator to use for producing Object Identifier for objects: either one of pre-defined
 * generators from [ObjectIdGenerator], or a custom generator. Defined as class to instantiate.
 *
 * Note that special type [ObjectIdGenerators.None] can be used to disable inclusion of Object Ids.
 *
 * @property property Name of CirJSON property in which Object Id will reside: also, if "from property" marker generator
 * is used, identifies property that will be accessed to get type id. If a property is used, name must match its
 * external name (one defined by annotation, or derived from accessor name as per Java Bean Introspection rules).
 *
 * Default value is `@id`.
 *
 * @property resolver Resolver to use for producing POJO from Object Identifier.
 *
 * Default value is [SimpleObjectIdResolver]
 *
 * @property scope Scope is used to define applicability of an Object ID: all ids must be unique within their scope;
 * where scope is defined as combination of this value and generator type. Comparison is simple equivalence, meaning
 * that both type generator type and scope class must be the same.
 *
 * Scope is used for determining how many generators are needed; more than one scope is typically only needed if
 * external Object Ids have overlapping value domains (i.e. are only unique within some limited scope)
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonIdentityInfo(val generator: KClass<out ObjectIdGenerator<*>>, val property: String = "@id",
        val resolver: KClass<out ObjectIdResolver> = SimpleObjectIdResolver::class, val scope: KClass<*> = Any::class)


