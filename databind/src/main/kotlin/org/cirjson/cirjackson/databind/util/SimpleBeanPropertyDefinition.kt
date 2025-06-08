package org.cirjson.cirjackson.databind.util

import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyMetadata
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.type.TypeFactory
import java.util.*
import kotlin.reflect.KClass

/**
 * Simple immutable [BeanPropertyDefinition] implementation that can be wrapped around a [AnnotatedMember] that is a
 * simple accessor (getter) or mutator (setter, constructor parameter) (or both, for fields).
 *
 * @property myMember Member that defines logical property. The assumption is that it should be a 'simple' accessor,
 * meaning a zero-argument getter, single-argument setter or constructor parameter.
 *
 * NOTE: for "virtual" properties, this is `null`.
 */
open class SimpleBeanPropertyDefinition protected constructor(protected val myConfig: MapperConfig<*>,
        protected val myMember: AnnotatedMember?, protected val myFullName: PropertyName, metadata: PropertyMetadata?,
        protected val myInclusion: CirJsonInclude.Value) : BeanPropertyDefinition() {

    protected val myMetadata = metadata ?: PropertyMetadata.STANDARD_OPTIONAL

    /*
     *******************************************************************************************************************
     * Fluent factories
     *******************************************************************************************************************
     */

    override fun withSimpleName(newSimpleName: String?): BeanPropertyDefinition {
        if (myFullName.hasSimpleName(newSimpleName) && !myFullName.hasNamespace()) {
            return this
        }

        return SimpleBeanPropertyDefinition(myConfig, myMember, PropertyName(newSimpleName), myMetadata, myInclusion)
    }

    override fun withName(newName: PropertyName): BeanPropertyDefinition {
        if (myFullName == newName) {
            return this
        }

        return SimpleBeanPropertyDefinition(myConfig, myMember, newName, myMetadata, myInclusion)
    }

    fun withMetadata(metadata: PropertyMetadata): BeanPropertyDefinition {
        if (myMetadata == metadata) {
            return this
        }

        return SimpleBeanPropertyDefinition(myConfig, myMember, myFullName, metadata, myInclusion)
    }

    fun withInclusion(inclusion: CirJsonInclude.Value): BeanPropertyDefinition {
        if (myInclusion == inclusion) {
            return this
        }

        return SimpleBeanPropertyDefinition(myConfig, myMember, myFullName, myMetadata, inclusion)
    }

    /*
     *******************************************************************************************************************
     * Basic property information, name, type
     *******************************************************************************************************************
     */

    override val name: String
        get() = myFullName.simpleName

    override val fullName: PropertyName
        get() = myFullName

    override fun hasName(name: PropertyName): Boolean {
        return myFullName == name
    }

    override val internalName: String
        get() = name

    override val wrapperName: PropertyName?
        get() {
            myMember ?: return null
            return myConfig.annotationIntrospector?.findWrapperName(myConfig, myMember)
        }

    override val isExplicitlyIncluded: Boolean
        get() = false

    override val isExplicitlyNamed: Boolean
        get() = false

    override val metadata: PropertyMetadata
        get() = myMetadata

    override val primaryType: KotlinType
        get() = myMember?.type ?: TypeFactory.unknownType()

    override val rawPrimaryType: KClass<*>
        get() = myMember?.rawType ?: Any::class

    override fun findInclusion(): CirJsonInclude.Value {
        return myInclusion
    }

    override fun findAliases(): List<PropertyName> {
        myMember ?: return emptyList()
        return myConfig.annotationIntrospector?.findPropertyAliases(myConfig, myMember) ?: emptyList()
    }

    /*
     *******************************************************************************************************************
     * Access to accessors (fields, methods, etc.)
     *******************************************************************************************************************
     */

    override fun hasGetter(): Boolean {
        return getter != null
    }

    override fun hasSetter(): Boolean {
        return setter != null
    }

    override fun hasField(): Boolean {
        return myMember is AnnotatedField
    }

    override fun hasConstructorParameter(): Boolean {
        return myMember is AnnotatedParameter
    }

    override val getter: AnnotatedMethod?
        get() = (myMember as? AnnotatedMethod)?.takeIf { it.parameterCount == 0 }

    override val setter: AnnotatedMethod?
        get() = (myMember as? AnnotatedMethod)?.takeIf { it.parameterCount == 1 }

    override val field: AnnotatedField?
        get() = myMember as? AnnotatedField

    override val constructorParameter: AnnotatedParameter?
        get() = myMember as? AnnotatedParameter

    override val constructorParameters: Iterator<AnnotatedParameter>
        get() {
            val param = constructorParameter ?: return emptyIterator()
            return Collections.singleton(param).iterator()
        }

    override val primaryMember: AnnotatedMember?
        get() = myMember

    companion object {

        /*
         ***************************************************************************************************************
         * Construction
         ***************************************************************************************************************
         */

        fun construct(config: MapperConfig<*>, member: AnnotatedMember): SimpleBeanPropertyDefinition {
            return SimpleBeanPropertyDefinition(config, member, PropertyName.construct(member.name), null,
                    CirJsonInclude.Value.EMPTY)
        }

        fun construct(config: MapperConfig<*>, member: AnnotatedMember?,
                name: PropertyName): SimpleBeanPropertyDefinition {
            return SimpleBeanPropertyDefinition(config, member, name, null, CirJsonInclude.Value.EMPTY)
        }

        fun construct(config: MapperConfig<*>, member: AnnotatedMember?, name: PropertyName,
                metadata: PropertyMetadata?, inclusion: CirJsonInclude.Include?): SimpleBeanPropertyDefinition {
            val inclusionValue = if (inclusion == null || inclusion == CirJsonInclude.Include.USE_DEFAULTS) {
                CirJsonInclude.Value.EMPTY
            } else {
                CirJsonInclude.Value.construct(inclusion, null)
            }

            return SimpleBeanPropertyDefinition(config, member, name, metadata, inclusionValue)
        }

        fun construct(config: MapperConfig<*>, member: AnnotatedMember?, name: PropertyName,
                metadata: PropertyMetadata?, inclusion: CirJsonInclude.Value): SimpleBeanPropertyDefinition {
            return SimpleBeanPropertyDefinition(config, member, name, metadata, inclusion)
        }

    }

}