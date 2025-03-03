package org.cirjson.cirjackson.annotations

/**
 * Definition of API used for resolving actual object from Object Identifiers (as annotated using
 * [CirJsonIdentityInfo]).
 */
interface ObjectIdResolver {

    /**
     * Method called when a POJO is deserialized and has an Object Identifier. Method exists so that implementation can
     * keep track of existing object in CirJSON stream that could be useful for further resolution.
     *
     * @param id The Object Identifier
     *
     * @param pojo The POJO associated to that Identifier
     */
    fun bindItem(id: ObjectIdGenerator.IDKey, pojo: Any)

    /**
     * Method called when deserialization encounters the given Object Identifier and requires the POJO associated with
     * it.
     *
     * @param id The Object Identifier
     *
     * @return The POJO, or .` if unable to resolve.
     */
    fun resolveId(id: ObjectIdGenerator.IDKey): Any?

    /**
     * Factory method called to create a new instance to use for deserialization: needed since resolvers may have state
     * (a pool of objects).
     *
     * Note that actual type of 'context' is `org.cirjson.cirjackson.databind.DeserializationContext`, but can not be
     * declared here as type itself (as well as call to this object) comes from databind package.
     *
     * @param context Deserialization context object used (of type
     * `org.cirjson.cirjackson.databind.DeserializationContext`); may be needed by more complex resolvers to access
     * contextual information such as configuration.
     */
    fun newForDeserialization(context: Any): ObjectIdResolver

    /**
     * Method called to check whether this resolver instance can be used for Object Ids of specific resolver type;
     * determination is based by passing a configured "blueprint" (prototype) instance; from which the actual instances
     * are created (using [newForDeserialization]).
     *
     * @return `true` if this instance can be used as-is; `false` if not
     */
    fun canUseFor(resolver: ObjectIdResolver): Boolean

}