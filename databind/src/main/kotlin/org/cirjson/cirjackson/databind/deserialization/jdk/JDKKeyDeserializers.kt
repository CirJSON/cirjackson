package org.cirjson.cirjackson.databind.deserialization.jdk

import org.cirjson.cirjackson.annotations.CirJsonCreator
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.deserialization.KeyDeserializers
import org.cirjson.cirjackson.databind.introspection.AnnotatedAndMetadata
import org.cirjson.cirjackson.databind.introspection.AnnotatedConstructor
import org.cirjson.cirjackson.databind.introspection.AnnotatedMember
import org.cirjson.cirjackson.databind.introspection.AnnotatedMethod
import org.cirjson.cirjackson.databind.util.*

/**
 * Helper class used to contain simple/well-known key deserializers. Following kinds of Objects can be handled currently:
 * 
 * * Primitive wrappers (Boolean, Byte, Char, Short, Integer, Float, Long, Double)
 * 
 * * Enums (usually not needed, since EnumMap doesn't call us)
 * 
 * * [java.util.Date]
 * 
 * * [java.util.Calendar]
 * 
 * * [java.util.UUID]
 * 
 * * [java.util.Locale]
 * 
 * * Anything with constructor that takes a single String arg (if not explicitly @CirJsonIgnore'd)
 * 
 * * Anything with `fun <T> valueOf(string: String)` companion method (if not explicitly @CirJsonIgnore'd)
 */
object JDKKeyDeserializers : KeyDeserializers {

    fun constructEnumKeyDeserializer(byNameResolver: EnumResolver, byEnumNamingResolver: EnumResolver?,
            byToStringResolver: EnumResolver, byIndexResolver: EnumResolver?): KeyDeserializer {
        return JDKKeyDeserializer.EnumKeyDeserializer(byNameResolver, null, byEnumNamingResolver, byToStringResolver,
                byIndexResolver)
    }

    fun constructEnumKeyDeserializer(byNameResolver: EnumResolver, factory: AnnotatedMethod?,
            byEnumNamingResolver: EnumResolver?, byToStringResolver: EnumResolver,
            byIndexResolver: EnumResolver?): KeyDeserializer {
        return JDKKeyDeserializer.EnumKeyDeserializer(byNameResolver, factory, byEnumNamingResolver, byToStringResolver,
                byIndexResolver)
    }

    fun constructDelegatingKeyDeserializer(type: KotlinType, deserializer: ValueDeserializer<*>): KeyDeserializer {
        return JDKKeyDeserializer.DelegatingKeyDeserializer(type.rawClass, deserializer)
    }

    fun findStringBasedKeyDeserializer(context: DeserializationContext, type: KotlinType): KeyDeserializer? {
        val beanDescription = context.introspectBeanDescriptionForCreation(type)
        val constructorInfo = findStringConstructor(beanDescription)

        if (constructorInfo?.metadata != null) {
            return constructCreatorKeyDeserializer(context, constructorInfo.annotated)
        }

        val factoryCandidates = beanDescription.factoryMethodsWithMode.asMutable()

        factoryCandidates.removeIf {
            it.annotated.parameterCount != 1 || it.annotated.getRawParameterType(0) != String::class ||
                    it.metadata == CirJsonCreator.Mode.PROPERTIES
        }

        val explicitFactory = findExplicitStringFactoryMethod(factoryCandidates)

        return if (explicitFactory != null) {
            constructCreatorKeyDeserializer(context, explicitFactory)
        } else if (constructorInfo != null) {
            constructCreatorKeyDeserializer(context, constructorInfo.annotated)
        } else if (factoryCandidates.isNotEmpty()) {
            constructCreatorKeyDeserializer(context, factoryCandidates[0].annotated)
        } else {
            null
        }
    }

    private fun constructCreatorKeyDeserializer(context: DeserializationContext,
            creator: AnnotatedMember): KeyDeserializer {
        if (creator is AnnotatedConstructor) {
            val rawConstructor = creator.annotated

            if (context.canOverrideAccessModifiers()) {
                rawConstructor.checkAndFixAccess(context.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
            }

            return JDKKeyDeserializer.StringConstructorKeyDeserializer(rawConstructor)
        }

        val method = (creator as AnnotatedMethod).annotated

        if (context.canOverrideAccessModifiers()) {
            method.checkAndFixAccess(context.isEnabled(MapperFeature.OVERRIDE_PUBLIC_ACCESS_MODIFIERS))
        }

        return JDKKeyDeserializer.StringFactoryKeyDeserializer(method)
    }

    private fun findStringConstructor(
            beanDescription: BeanDescription): AnnotatedAndMetadata<AnnotatedConstructor, CirJsonCreator.Mode>? {
        for (entry in beanDescription.constructorsWithMode) {
            val constructor = entry.annotated

            if (constructor.parameterCount == 1 && constructor.getRawParameterType(0) == String::class) {
                return entry
            }
        }

        return null
    }

    private fun findExplicitStringFactoryMethod(
            candidates: List<AnnotatedAndMetadata<AnnotatedMethod, CirJsonCreator.Mode>>): AnnotatedMethod? {
        var match: AnnotatedMethod? = null

        for (entry in candidates) {
            if (entry.metadata != null) {
                if (match != null) {
                    val rawKeyType = entry.annotated.declaringClass
                    throw IllegalArgumentException(
                            "Multiple suitable annotated Creator factory methods to be used as the Key deserializer for type ${rawKeyType.name}")
                }

                match = entry.annotated
            }
        }

        return match
    }

    /*
     *******************************************************************************************************************
     * KeyDeserializers implementation
     *******************************************************************************************************************
     */

    override fun findKeyDeserializer(type: KotlinType, config: DeserializationConfig,
            beanDescription: BeanDescription): KeyDeserializer? {
        val raw = type.rawClass
        return JDKKeyDeserializer.forType(raw.takeIf { it.isPrimitive }?.wrapperType() ?: raw)
    }

}