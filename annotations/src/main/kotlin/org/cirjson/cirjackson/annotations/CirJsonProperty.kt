package org.cirjson.cirjackson.annotations

/**
 * Marker annotation that can be used to define a non-static method as a "setter" or "getter" for a logical property
 * accessor (depending on its signature), or a non-static Object field to be used (serialized, deserialized) as a
 * logical property (to assign value or get value from)
 *
 * Value ("") indicates that the name of field (or, derived name of an accessor method (setter / getter)) is to be used
 * as the property name without any modifications; a non-empty value can be used to specify a different name. Property
 * name refers to the name used externally, as the property name in CirJSON objects (as opposed to internal name of
 * field in Object).
 *
 * This annotation may also be used to change serialization of `Enum` like so:
 * ```
 * enum class MyEnum {
 *     @CirJsonProperty("theFirstValue") THE_FIRST_VALUE,
 *     @CirJsonProperty("another_value") ANOTHER_VALUE;
 * }
 * ```
 * as an alternative to using [CirJsonValue] annotation.
 *
 * It is also possible to specify `namespace` of property: this property is only used by certain format backends (most
 * notably XML).
 *
 * @property value Defines name of the logical property, i.e. CirJSON object field name to use for the property. If
 * value is empty String (which is the default), will try to use name of the field that is annotated. Note that there is
 * **no default name available for constructor arguments**, meaning that **Empty String is not a valid value for
 * constructor arguments**.
 *
 * @property namespace Optional namespace to use with data formats that support such concept (specifically XML); if so,
 * used with [value] to construct fully-qualified name.
 *
 * @property required Property that indicates whether a value (which may be explicit `null`) is expected for property
 * during deserialization or not. If expected, `BeanDeserialized` should indicate this as a validity problem (usually by
 * throwing an exception, but this may be sent via problem handlers that can try to rectify the problem, for example, by
 * supplying a default value).

 * Note that this property is only used for Creator Properties, to ensure existence of property value in CirJSON: for
 * other properties (ones injected using a setter or mutable field), no validation is performed. Support for those cases
 * may be added in the future. State of this property is exposed via introspection, and its value is typically used by
 * Schema generators, such as one for CirJSON Schema.
 *
 * Also note that the required value must come **directly** from the input source (e.g., CirJSON) and not from secondary
 * sources, such as defaulting logic or absent value providers. If secondary sources are expected to supply the value,
 * this property should be set to `false`. This is important because validation of `required` properties occurs before
 * the application of secondary sources.
 *
 * @property index Property that indicates numerical index of this property (relative to other properties specified for
 * the Object). This index is typically used by binary formats, but may also be useful for schema languages and other
 * tools.
 *
 * @property defaultValue Property that may be used to **document** expected default value for the property: most often
 * used as source information for generating schemas (like CirJSON Schema or protobuf/thrift schema), or documentation.
 * It may also be used by CirJackson extension modules; core `jackson-databind` does not have any automated handling
 * beyond simply exposing this value through bean property introspection.
 *
 * It is possible that in future this annotation could be used for value defaulting, and especially for default values
 * of Creator properties, since they support [required].
 *
 * @property access Optional property that may be used to change the way visibility of accessors (getter,
 * field-as-getter) and mutators (constructor parameter, setter, field-as-setter) is determined, either so that
 * otherwise non-visible accessors (like private getters) may be used; or that otherwise visible accessors are ignored.
 *
 * Default value os [Access.AUTO] which means that access is determined solely based on visibility and other
 * annotations.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION, AnnotationTarget.FIELD,
        AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonProperty(val value: String = USE_DEFAULT_NAME, val namespace: String = "",
        val required: Boolean = false, val index: Int = INDEX_UNKNOWN, val defaultValue: String = "",
        val access: Access = Access.AUTO) {

    /**
     * Various options for [access] property, specifying how property may be accessed during serialization ("read") and
     * deserialization ("write") (note that the direction of read and write is from perspective of the property, not
     * from external data format: this may be confusing in some contexts).
     *
     * Note that while this annotation modifies access to annotated property, its effects may be further overridden by
     * [CirJsonIgnore] property: if both annotations are present on an accessors, [CirJsonIgnore] has precedence over
     * this property. This annotation property is, however, preferred over use of "split"
     * [CirJsonIgnore]/`CirJsonProperty` combination.
     */
    enum class Access {

        /**
         * Access setting which means that visibility rules are to be used to automatically determine read- and/or
         * write-access of this property.
         */
        AUTO,

        /**
         * Access setting that means that the property may only be read for serialization (value accessed via "getter"
         * Method, or read from Field) but not written (set) during deserialization. Put another way, this would reflect
         * "read-only POJO", in which value contained may be read but not written/set.
         */
        READ_ONLY,

        /**
         * Access setting that means that the property may only be written (set) as part of deserialization (using
         * "setter" method, or assigning to Field, or passed as Creator argument) but will not be read (get) for
         * serialization, that is, the value of the property is not included in serialization.
         */
        WRITE_ONLY,

        /**
         * Access setting that means that the property will be accessed for both serialization (writing out values as
         * external representation) and deserialization (reading values from external representation), regardless of
         * visibility rules.
         */
        READ_WRITE

    }

    companion object {

        /**
         * Special value that indicates that handlers should use the default name (derived from method or field name)
         * for property.
         */
        const val USE_DEFAULT_NAME = ""

        /**
         * Marker value used to indicate that no index has been specified. Used as the default value as annotations do
         * not allow "missing" values.
         */
        const val INDEX_UNKNOWN = -1

    }

}
