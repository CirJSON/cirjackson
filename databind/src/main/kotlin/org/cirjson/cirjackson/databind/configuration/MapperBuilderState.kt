package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.PrettyPrinter
import org.cirjson.cirjackson.core.TokenStreamFactory
import org.cirjson.cirjackson.databind.AbstractTypeResolver
import org.cirjson.cirjackson.databind.CirJacksonModule
import org.cirjson.cirjackson.databind.InjectableValues
import org.cirjson.cirjackson.databind.ObjectMapper
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.cirjsontype.TypeResolverProvider
import org.cirjson.cirjackson.databind.deserialization.DeserializationProblemHandler
import org.cirjson.cirjackson.databind.deserialization.DeserializerFactory
import org.cirjson.cirjackson.databind.introspection.ClassIntrospector
import org.cirjson.cirjackson.databind.introspection.MixInHandler
import org.cirjson.cirjackson.databind.serialization.FilterProvider
import org.cirjson.cirjackson.databind.serialization.SerializerFactory
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.LinkedNode

/**
 * Interface for the State object used for preserving initial state of a [MapperBuilder] before modules are configured
 * and the resulting [ObjectMapper] isn't constructed. It is passed to mapper to allow "re-building" via newly created
 * builder.
 *
 * Note that JDK serialization is supported by switching this object in place of mapper. This requires some acrobatics
 * on the `return` direction.
 */
abstract class MapperBuilderState(source: MapperBuilder<*, *>) {

    /*
     *******************************************************************************************************************
     * Basic settings
     *******************************************************************************************************************
     */

    protected val myBaseSettings: BaseSettings

    internal fun internalBaseSettings(): BaseSettings {
        return myBaseSettings
    }

    protected val myStreamFactory: TokenStreamFactory

    internal fun internalStreamFactory(): TokenStreamFactory {
        return myStreamFactory
    }

    protected val myConfigOverrides: ConfigOverrides

    internal fun internalConfigOverrides(): ConfigOverrides {
        return myConfigOverrides
    }

    protected val myCoercionConfigs: CoercionConfigs

    internal fun internalCoercionConfigs(): CoercionConfigs {
        return myCoercionConfigs
    }

    /*
     *******************************************************************************************************************
     * Modules
     *******************************************************************************************************************
     */

    protected val myModules: Array<CirJacksonModule>?

    internal fun internalModules(): Array<CirJacksonModule>? {
        return myModules
    }

    /*
     *******************************************************************************************************************
     * Handlers, introspection
     *******************************************************************************************************************
     */

    protected val myTypeFactory: TypeFactory?

    internal fun internalTypeFactory(): TypeFactory? {
        return myTypeFactory
    }

    protected val myClassIntrospector: ClassIntrospector?

    internal fun internalClassIntrospector(): ClassIntrospector? {
        return myClassIntrospector
    }

    protected val myTypeResolverProvider: TypeResolverProvider?

    internal fun internalTypeResolverProvider(): TypeResolverProvider? {
        return myTypeResolverProvider
    }

    protected val mySubtypeResolver: SubtypeResolver?

    internal fun internalSubtypeResolver(): SubtypeResolver? {
        return mySubtypeResolver
    }

    protected val myMixInHandler: MixInHandler?

    internal fun internalMixInHandler(): MixInHandler? {
        return myMixInHandler
    }

    /*
     *******************************************************************************************************************
     * Factories for serialization
     *******************************************************************************************************************
     */

    protected val mySerializationContexts: SerializationContexts?

    internal fun internalSerializationContexts(): SerializationContexts? {
        return mySerializationContexts
    }

    protected val mySerializerFactory: SerializerFactory?

    internal fun internalSerializerFactory(): SerializerFactory? {
        return mySerializerFactory
    }

    protected val myFilterProvider: FilterProvider?

    internal fun internalFilterProvider(): FilterProvider? {
        return myFilterProvider
    }

    protected val myDefaultPrettyPrinter: PrettyPrinter?

    internal fun internalDefaultPrettyPrinter(): PrettyPrinter? {
        return myDefaultPrettyPrinter
    }

    /*
     *******************************************************************************************************************
     * Factories for deserialization
     *******************************************************************************************************************
     */

    protected val myDeserializationContexts: DeserializationContexts?

    internal fun internalDeserializationContexts(): DeserializationContexts? {
        return myDeserializationContexts
    }

    protected val myDeserializerFactory: DeserializerFactory?

    internal fun internalDeserializerFactory(): DeserializerFactory? {
        return myDeserializerFactory
    }

    protected val myInjectableValues: InjectableValues?

    internal fun internalInjectableValues(): InjectableValues? {
        return myInjectableValues
    }

    /**
     * Optional handlers that application may register to try to work around various problem situations during
     * deserialization
     */
    protected val myProblemHandlers: LinkedNode<DeserializationProblemHandler>?

    internal fun internalProblemHandlers(): LinkedNode<DeserializationProblemHandler>? {
        return myProblemHandlers
    }

    protected val myAbstractTypeResolvers: Array<AbstractTypeResolver>

    internal fun internalAbstractTypeResolvers(): Array<AbstractTypeResolver> {
        return myAbstractTypeResolvers
    }

    /*
     *******************************************************************************************************************
     * Handlers/factories, other:
     *******************************************************************************************************************
     */

    protected val myDefaultAttributes: ContextAttributes?

    internal fun internalDefaultAttributes(): ContextAttributes? {
        return myDefaultAttributes
    }

    /*
     *******************************************************************************************************************
     * Feature flags: serialization, deserialization
     *******************************************************************************************************************
     */

    protected val myMapperFeatures: Long

    internal fun internalMapperFeatures(): Long {
        return myMapperFeatures
    }

    protected val mySerializationFeatures: Int

    internal fun internalSerializationFeatures(): Int {
        return mySerializationFeatures
    }

    protected val myDeserializationFeatures: Int

    internal fun internalDeserializationFeatures(): Int {
        return myDeserializationFeatures
    }

    protected val myDatatypeFeatures: DatatypeFeatures?

    internal fun internalDatatypeFeatures(): DatatypeFeatures? {
        return myDatatypeFeatures
    }

    /*
     *******************************************************************************************************************
     * Feature flags: generation, parsing
     *******************************************************************************************************************
     */

    protected val myStreamReadFeature: Int

    internal fun internalStreamReadFeature(): Int {
        return myStreamReadFeature
    }

    protected val myStreamWriteFeature: Int

    internal fun internalStreamWriteFeature(): Int {
        return myStreamWriteFeature
    }

    protected val myFormatReadFeatures: Int

    internal fun internalFormatReadFeatures(): Int {
        return myFormatReadFeatures
    }

    protected val myFormatWriteFeatures: Int

    internal fun internalFormatWriteFeatures(): Int {
        return myFormatWriteFeatures
    }

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    init {
        myBaseSettings = source.internalBaseSettings()
        myStreamFactory = source.internalStreamFactory()
        myConfigOverrides = source.internalConfigOverrides().snapshot()
        myCoercionConfigs = source.internalCoercionConfigs().snapshot()

        myModules = source.internalModules()?.values?.toTypedArray()

        myTypeFactory = source.internalTypeFactory()?.snapshot()
        myClassIntrospector = source.internalClassIntrospector()
        myTypeResolverProvider = source.internalTypeResolverProvider()
        mySubtypeResolver = source.internalSubtypeResolver()?.snapshot()
        myMixInHandler = source.internalMixInHandler()?.snapshot() as MixInHandler?

        mySerializationContexts = source.internalSerializationContexts()
        mySerializerFactory = source.internalSerializerFactory()
        myFilterProvider = source.internalFilterProvider()?.snapshot()
        myDefaultPrettyPrinter = source.internalDefaultPrettyPrinter()

        myDeserializationContexts = source.internalDeserializationContexts()
        myDeserializerFactory = source.internalDeserializerFactory()
        myInjectableValues = source.internalInjectableValues()?.snapshot()
        myProblemHandlers = source.internalProblemHandlers()
        myAbstractTypeResolvers = source.internalAbstractTypeResolvers()

        myDefaultAttributes = source.internalDefaultAttributes()?.snapshot()

        myMapperFeatures = source.internalMapperFeatures()
        mySerializationFeatures = source.internalSerializationFeatures()
        myDeserializationFeatures = source.internalDeserializationFeatures()
        myDatatypeFeatures = source.internalDatatypeFeatures()
        myStreamReadFeature = source.internalStreamReadFeature()
        myStreamWriteFeature = source.internalStreamWriteFeature()
        myFormatReadFeatures = source.internalFormatReadFeatures()
        myFormatWriteFeatures = source.internalFormatWriteFeatures()
    }

    /*
     *******************************************************************************************************************
     * Configuration access by ObjectMapper
     *******************************************************************************************************************
     */

    fun modules(): List<CirJacksonModule> {
        return myModules?.asList() ?: emptyList()
    }

}