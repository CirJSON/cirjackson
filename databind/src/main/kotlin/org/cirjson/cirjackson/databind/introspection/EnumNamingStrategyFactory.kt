package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.EnumNamingStrategy
import org.cirjson.cirjackson.databind.util.className
import org.cirjson.cirjackson.databind.util.createInstance
import org.cirjson.cirjackson.databind.util.isAssignableFrom
import kotlin.reflect.KClass

/**
 * Helper class used for aggregating information about all possible properties of an Enum.
 */
object EnumNamingStrategyFactory {

    /**
     * Factory method for creating an instance of [EnumNamingStrategy] from a provided `namingDefinition`.
     *
     * @param namingDefinition subclass of [EnumNamingStrategy] to initialize an instance of.
     *
     * @param canOverrideAccessModifiers whether to override access modifiers when instantiating the naming strategy.
     *
     * @return an instance of [EnumNamingStrategy] if `namingDefinition` is a subclass of [EnumNamingStrategy], `null`
     * if `namingDefinition` is `null`, and an instance of [EnumNamingStrategy] if `namingDefinition` already is one.
     *
     * @throws IllegalArgumentException if `namingDefinition` is not an instance of [KClass] or not a subclass of
     * [EnumNamingStrategy].
     */
    fun createEnumNamingStrategyInstance(namingDefinition: Any?,
            canOverrideAccessModifiers: Boolean): EnumNamingStrategy? {
        namingDefinition ?: return null

        if (namingDefinition is EnumNamingStrategy) {
            return namingDefinition
        }

        if (namingDefinition !is KClass<*>) {
            throw IllegalArgumentException(
                    "AnnotationIntrospector returned EnumNamingStrategy definition of type ${namingDefinition.className}; expected type `KClass<EnumNamingStrategy>` instead")
        }

        if (namingDefinition == EnumNamingStrategy::class) {
            return null
        }

        if (!EnumNamingStrategy::class.isAssignableFrom(namingDefinition)) {
            throw IllegalArgumentException(
                    "Problem with AnnotationIntrospector returned KClass ${namingDefinition.className}; expected `KClass<EnumNamingStrategy>`")
        }

        return namingDefinition.createInstance(canOverrideAccessModifiers) as EnumNamingStrategy
    }

}