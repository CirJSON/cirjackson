package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.configuration.MapperConfig

/**
 * API for handlers used to "mangle" names of "getter" and "setter" methods to find implicit property names.
 */
abstract class AccessorNamingStrategy {

    /**
     * Method called to find whether given method would be considered an "is-getter" getter method in context of type
     * introspected, and if so, what is the logical property it is associated with (which in turn suggest external name
     * for property)
     *
     * Note that signature acceptability has already been checked (no arguments, has return value) but NOT the specific
     * limitation that return type should be of boolean type -- implementation should apply latter check, if so desired
     * (some languages may use different criteria). It is also possible that some implementations allow different return
     * types than boolean types.
     *
     * Note that visibility checks are applied separately; strategy does not need to be concerned with that aspect.
     *
     * @param method Method to check
     *
     * @param name Name to check (usually same as [AnnotatedMethod.name]
     *
     * @return Implied property name for is-getter method, if matched; `null` to indicate that the name does not conform
     * to expected naming convention
     */
    abstract fun findNameForIsGetter(method: AnnotatedMethod, name: String): String?

    /**
     * Method called to find whether given method would be considered a "regular" getter method in context of type
     * introspected, and if so, what is the logical property it is associated with (which in turn suggest external name
     * for property).
     *
     * Note that signature acceptability has already been checked (no arguments, does have a return value) by caller.
     *
     * Note that this method MAY be called for potential "is-getter" methods too (before [findNameForIsGetter])
     *
     * Note that visibility checks are applied separately; strategy does not need to be concerned with that aspect.
     *
     * @param method Method to check
     *
     * @param name Name to check (usually same as [AnnotatedMethod.name]
     *
     * @return Implied property name for getter method, if matched; `null` to indicate that the name does not conform to
     * expected naming convention
     */
    abstract fun findNameForRegularGetter(method: AnnotatedMethod, name: String): String?

    /**
     * Method called to find whether given method would be considered a "mutator" (usually setter, but for builders
     * "with-method" or similar) in context of type introspected, and if so, what is the logical property it is
     * associated with (which in turn suggest external name for property)
     *
     * Note that signature acceptability has already been checked (exactly one parameter) by caller.
     *
     * Note that visibility checks are applied separately; strategy does not need to be concerned with that aspect.
     *
     * @param method Method to check
     *
     * @param name Name to check (usually same as [AnnotatedMethod.name]
     *
     * @return Implied property name for mutator method, if matched; `null` to indicate that the name does not conform
     * to expected naming convention
     */
    abstract fun findNameForMutator(method: AnnotatedMethod, name: String): String?

    /**
     * Method called to find the name of logical property that given field should be associated with, if any.
     *
     * Note that visibility checks are applied separately; strategy does not need to be concerned with that aspect.
     *
     * @param field Field to check
     *
     * @param name Name to check (usually same as [AnnotatedField.name]
     *
     * @return Implied property name matching given field (often field name as-is) or `null` to indicate that the name
     * does not conform to expected naming convention (and will not be considered for property access)
     */
    abstract fun modifyFieldName(field: AnnotatedField, name: String): String?

    /**
     * Helper class that implements all abstract methods with dummy implementations. Behavior is as follows:
     *
     * * No getter or is-getter methods are recognized: relevant methods return `null`
     *
     * * No setter methods are recognized: relevant methods return `null`
     *
     * * Names of fields are returned as-is, without modifications (meaning they may be discovered if they are otherwise
     * visible
     */
    open class Base : AccessorNamingStrategy() {

        override fun findNameForIsGetter(method: AnnotatedMethod, name: String): String? {
            return null
        }

        override fun findNameForRegularGetter(method: AnnotatedMethod, name: String): String? {
            return null
        }

        override fun findNameForMutator(method: AnnotatedMethod, name: String): String? {
            return null
        }

        override fun modifyFieldName(field: AnnotatedField, name: String): String? {
            return name
        }

    }

    /**
     * Interface for provider (factory) for constructing [AccessorNamingStrategy] for given type of deserialization
     * target.
     */
    abstract class Provider {

        /**
         * Factory method for creating strategy instance for a "regular" POJO, called if none of the other factory
         * methods is applicable.
         *
         * @param config Current mapper configuration
         *
         * @param valueClass Information about value type
         *
         * @return Naming strategy instance to use
         */
        abstract fun forPOJO(config: MapperConfig<*>, valueClass: AnnotatedClass): AccessorNamingStrategy

        /**
         * Factory method for creating strategy instance for POJOs that are deserialized using Builder type: in this
         * case eventual target (value) type is different from type of "builder" object that is used by databinding to
         * accumulate state.
         *
         * @param config Current mapper configuration
         *
         * @param builderClass Information about builder type
         *
         * @param valueTypeDescription Information about the eventual target (value) type
         *
         * @return Naming strategy instance to use
         */
        abstract fun forBuilder(config: MapperConfig<*>, builderClass: AnnotatedClass,
                valueTypeDescription: BeanDescription): AccessorNamingStrategy

        /**
         * Factory method for creating strategy instance for special `Record` type (new in JDK 14).
         *
         * @param config Current mapper configuration
         *
         * @param recordClass Information about value type (of type `Record`)
         *
         * @return Naming strategy instance to use
         */
        abstract fun forRecord(config: MapperConfig<*>, recordClass: AnnotatedClass): AccessorNamingStrategy

    }

}