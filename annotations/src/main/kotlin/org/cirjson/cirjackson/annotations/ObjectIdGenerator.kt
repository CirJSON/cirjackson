package org.cirjson.cirjackson.annotations

import kotlin.reflect.KClass

/**
 * Definition of API used for constructing Object Identifiers (as annotated using [CirJsonIdentityInfo]). Also defines
 * factory methods used for creating instances for serialization, deserialization.
 *
 * @param T Type of Object Identifiers produced.
 */
abstract class ObjectIdGenerator<T> {

    abstract val scope: KClass<*>

    /**
     * Method called to check whether this generator instance can be used for Object Ids of specific generator type and
     * scope; determination is based by passing a configured "blueprint" (prototype) instance; from which the actual
     * instances are created (using [newForSerialization]).
     *
     * @return `true` if this instance can be used as-is; `false` if not
     */
    abstract fun canUseFor(generator: ObjectIdGenerator<*>): Boolean

    /**
     * Accessor that needs to be overridden to return `true` if the Object ID may be serialized as JSON Object; used by,
     * for example, JSOG handling. The reason accessor is needed is that handling such Object Ids is more complex and
     * may incur additional buffering or performance overhead, avoiding of which makes sense for common case of scalar
     * object ids (or native object ids some formats support).
     *
     * Default implementation returns `false`, so needs to be overridden by Object-producing generators.
     */
    open fun maySerializeAsObject(): Boolean {
        return false
    }

    /**
     * Accessor that may be called (after verifying (via [maySerializeAsObject]) whether given name
     *
     * @param name Name of property to check
     *
     * @param parser Parser that points to property name, in case generator needs further verification (note: untyped,
     * because `CirJsonParser` is defined in `cirjackson-core`, and this package does not depend on it).
     */
    open fun isValidReferencePropertyName(name: String, parser: Any): Boolean {
        return false
    }

    /**
     * Factory method to create a blueprint instance for specified scope. Generators that do not use scope may return
     * 'this'.
     */
    abstract fun forScope(scope: KClass<*>): ObjectIdGenerator<T>

    /**
     * Factory method called to create a new instance to use for serialization: needed since generators may have state
     * (next id to produce).
     *
     * Note that actual type of 'context' is `org.cirjson.cirjackson.databind.SerializerProvider`, but can not be
     * declared here as type itself (as well as call to this object) comes from databind package.
     *
     * @param context Serialization context object used (of type `org.cirjson.cirjackson.databind.SerializerProvider`);
     * may be needed by more complex generators to access contextual information such as configuration.
     */
    abstract fun newForSerialization(context: Any): ObjectIdGenerator<T>

    /**
     * Method for constructing key to use for ObjectId-to-POJO maps.
     */
    abstract fun key(key: Any?): IDKey?

    /**
     * Method used for generating a new Object Identifier to serialize for given POJO.
     *
     * @param forPojo POJO for which identifier is needed
     *
     * @return Object Identifier to use.
     */
    abstract fun generateId(forPojo: Any?): T?

    /**
     * Simple key class that can be used as a key for ObjectId-to-POJO mappings, when multiple ObjectId types and scopes
     * are used.
     *
     * @property type Type of [ObjectIdGenerator] used for generating Object ID
     *
     * @property scope Scope of the Object ID (it may be `null`, to denote global)
     *
     * @property key Object for which Object ID was generated: can NOT be `null`.
     */
    class IDKey(val type: KClass<*>, val scope: KClass<*>?, val key: Any) {

        private val hasCode = let {
            var hash = key.hashCode() + type.qualifiedName.hashCode()

            if (scope != null) {
                hash = hash xor scope.qualifiedName.hashCode()
            }

            hash
        }

        override fun hashCode(): Int {
            return hasCode
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }

            if (javaClass != other?.javaClass) {
                return false
            }

            other as IDKey

            return key == other.key && type == other.type && scope == other.scope
        }

        override fun toString(): String {
            return "[ObjectId: key=$key, type=${type.qualifiedName}, scope=${scope?.qualifiedName ?: "NONE"}]"
        }

    }

}