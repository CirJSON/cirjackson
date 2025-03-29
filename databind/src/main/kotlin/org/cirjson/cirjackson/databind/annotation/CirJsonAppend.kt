package org.cirjson.cirjackson.databind.annotation

import org.cirjson.cirjackson.annotations.CirJacksonAnnotation
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.serialization.VirtualBeanPropertyWriter
import kotlin.reflect.KClass

/**
 * Annotation used to add "virtual" properties that will be written after regular properties during serialization.
 *
 * Please note that the "virtual" properties added using this annotation do not obey any specific order, including the
 * order defined by [org.cirjson.cirjackson.annotations.CirJsonPropertyOrder].
 *
 * @property attributes Set of attribute-backed properties to include when serializing a POJO.
 *
 * @property properties Set of general virtual properties to include when serializing a POJO.
 *
 * @property prepend Indicator used to determine whether properties defined are to be appended after (`false`) or
 * prepended before (`true`) regular properties. Affects all kinds of properties defined using this annotation.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonAppend(val attributes: Array<Attribute> = [], val properties: Array<Property> = [],
        val prepend: Boolean = false) {

    /**
     * Definition of a single attribute-backed property. Attribute-backed properties will be appended after (or
     * prepended before, as per [prepend]) regular properties in specified order, although their placement may be
     * further changed by the usual property-ordering functionality (alphabetic sorting; explicit ordering)
     *
     * @property value Name of attribute of which value to serialize. Is also used as the name of external property to
     * write, unless overridden by assigning a value for [propertyName].
     *
     * @property propertyName Name to use for serializing value of the attribute; if not defined, [value] will be used
     * instead.
     *
     * @property propertyNamespace Optional namespace to use; only relevant for data formats that use namespaces (like
     * XML).
     *
     * @property include When to include attribute-property. Default value indicates that property should only be
     * written if specified attribute has a non-null value.
     *
     * @property required Metadata about property, similar to
     * [org.cirjson.cirjackson.annotations.CirJsonProperty.required].
     */
    annotation class Attribute(val value: String, val propertyName: String = "", val propertyNamespace: String = "",
            val include: CirJsonInclude.Include = CirJsonInclude.Include.NON_NULL, val required: Boolean = false)

    /**
     * Definition of a single general virtual property.
     *
     * @property value Actual implementation class (a subtype of [VirtualBeanPropertyWriter]) of the property to
     * instantiate (using the no-argument default constructor).
     *
     * @property name Name of the property to possibly use for serializing (although implementation may choose to not
     * use this information).
     *
     * @property namespace Optional namespace to use along with [name]; only relevant for data formats that use
     * namespaces (like XML).
     *
     * @property include When to include  value of the property. Default value indicates that property should only be
     * written if specified attribute has a non-null value. As with other properties, actual property implementation may
     * or may not choose to use this inclusion information.
     *
     * @property required Metadata about property, similar to
     * [org.cirjson.cirjackson.annotations.CirJsonProperty.required].
     *
     * @property type Nominal type of the property. Passed as type information for related virtual objects, and may (or
     * may not be) used by implementation for choosing serializer to use.
     */
    annotation class Property(val value: KClass<out VirtualBeanPropertyWriter>, val name: String = "",
            val namespace: String = "", val include: CirJsonInclude.Include = CirJsonInclude.Include.NON_NULL,
            val required: Boolean = false, val type: KClass<*> = Any::class)

}
