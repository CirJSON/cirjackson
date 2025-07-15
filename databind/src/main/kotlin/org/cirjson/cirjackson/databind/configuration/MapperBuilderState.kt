package org.cirjson.cirjackson.databind.configuration

/**
 * Interface for the State object used for preserving initial state of a [MapperBuilder] before modules are configured
 * and the resulting [ObjectMapper] isn't constructed. It is passed to mapper to allow "re-building" via newly created
 * builder.
 *
 * Note that JDK serialization is supported by switching this object in place of mapper. This requires some acrobatics
 * on the `return` direction.
 */
abstract class MapperBuilderState {
}