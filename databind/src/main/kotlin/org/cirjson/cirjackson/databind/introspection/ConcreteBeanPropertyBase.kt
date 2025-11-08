package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.BeanProperty
import org.cirjson.cirjackson.databind.PropertyMetadata
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import kotlin.reflect.KClass

/**
 * Intermediate [BeanProperty] class shared by concrete readable- and writable property implementations for sharing
 * common functionality.
 */
abstract class ConcreteBeanPropertyBase protected constructor(metadata: PropertyMetadata?) : BeanProperty {

    /**
     * Additional information about property
     */
    protected val myMetadata = metadata ?: PropertyMetadata.STANDARD_REQUIRED_OR_OPTIONAL

    protected var myAliases: List<PropertyName>? = null

    constructor(source: ConcreteBeanPropertyBase) : this(source.metadata)

    override val isRequired: Boolean
        get() = myMetadata.isRequired

    override val metadata: PropertyMetadata
        get() = myMetadata

    override val isVirtual: Boolean
        get() = false

    override fun findFormatOverrides(config: MapperConfig<*>): CirJsonFormat.Value? {
        val introspector = config.annotationIntrospector ?: return null
        val member = member ?: return null
        return introspector.findFormat(config, member)
    }

    override fun findPropertyFormat(config: MapperConfig<*>, baseType: KClass<*>): CirJsonFormat.Value {
        val v1 = config.getDefaultPropertyFormat(baseType)
        val v2 = findFormatOverrides(config) ?: return v1
        return v1.withOverrides(v2)
    }

    override fun findPropertyInclusion(config: MapperConfig<*>, baseType: KClass<*>): CirJsonInclude.Value? {
        val introspector = config.annotationIntrospector
        val member = member ?: return config.getDefaultPropertyInclusion(baseType)
        val v0 = config.getDefaultInclusion(baseType, member.rawType)

        introspector ?: return v0

        val v = introspector.findPropertyInclusion(config, member)
        return v0?.withOverrides(v) ?: v
    }

    override fun findAliases(config: MapperConfig<*>): List<PropertyName> {
        var aliases = myAliases

        if (aliases != null) {
            return aliases
        }

        val introspector = config.annotationIntrospector

        if (introspector != null) {
            val member = member

            if (member != null) {
                aliases = introspector.findPropertyAliases(config, member)
            }
        }

        if (aliases == null) {
            aliases = emptyList()
        }

        myAliases = aliases
        return aliases
    }

}