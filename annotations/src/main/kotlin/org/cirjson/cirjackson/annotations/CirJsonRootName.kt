package org.cirjson.cirjackson.annotations

/**
 * Annotation similar to JAXB `javax.xml.bind.annotation.XmlRootElement`, used to indicate name to use for root-level
 * wrapping, if wrapping is enabled. Annotation itself does not indicate that wrapping should be used; but if it is, the
 * name used for serialization should be the name specified here, and deserializer will expect the name as well.
 *
 * @property value Root name to use if root-level wrapping is enabled. For data formats that use composite names (XML),
 * this is the "local part" of the name to use.
 *
 * @property namespace Optional namespace to use with data formats that support such concept (specifically XML); if so,
 * used with [value] to construct fully-qualified name.
 */
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@CirJacksonAnnotation
annotation class CirJsonRootName(val value: String, val namespace: String = "")
