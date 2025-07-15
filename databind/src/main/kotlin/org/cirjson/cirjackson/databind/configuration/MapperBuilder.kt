package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.core.Base64Variants
import org.cirjson.cirjackson.core.PrettyPrinter
import org.cirjson.cirjackson.core.util.DefaultPrettyPrinter
import org.cirjson.cirjackson.databind.*
import org.cirjson.cirjackson.databind.cirjsontype.DefaultBaseTypeLimitingValidator
import org.cirjson.cirjackson.databind.cirjsontype.PolymorphicTypeValidator
import org.cirjson.cirjackson.databind.cirjsontype.TypeResolverProvider
import org.cirjson.cirjackson.databind.introspection.AccessorNamingStrategy
import org.cirjson.cirjackson.databind.introspection.CirJacksonAnnotationIntrospector
import org.cirjson.cirjackson.databind.introspection.DefaultAccessorNamingStrategy
import org.cirjson.cirjackson.databind.node.CirJsonNodeFactory
import org.cirjson.cirjackson.databind.util.StandardDateFormat
import java.util.*

abstract class MapperBuilder<M : ObjectMapper, B : MapperBuilder<M, B>> {

    /*
     *******************************************************************************************************************
     * Accessors, base settings
     *******************************************************************************************************************
     */

    fun baseSettings(): BaseSettings {
        TODO("Not yet implemented")
    }

    companion object {

        val DEFAULT_MAPPER_FEATURES = MapperFeature.collectLongDefaults()

        val DEFAULT_SERIALIZATION_FEATURES = ConfigFeature.collectFeatureDefaults(SerializationFeature::class)

        val DEFAULT_DESERIALIZATION_FEATURES = ConfigFeature.collectFeatureDefaults(DeserializationFeature::class)

        val DEFAULT_PRETTY_PRINTER: PrettyPrinter = DefaultPrettyPrinter()

        val DEFAULT_ANNOTATION_INTROSPECTOR: AnnotationIntrospector = CirJacksonAnnotationIntrospector()

        val DEFAULT_TYPE_VALIDATOR: PolymorphicTypeValidator = DefaultBaseTypeLimitingValidator()

        val DEFAULT_ACCESSOR_NAMING: AccessorNamingStrategy.Provider = DefaultAccessorNamingStrategy.Provider()

        val DEFAULT_BASE_SETTINGS = BaseSettings(DEFAULT_ANNOTATION_INTROSPECTOR, null, DEFAULT_ACCESSOR_NAMING, null,
                DEFAULT_TYPE_VALIDATOR, StandardDateFormat.instance, null, Locale.getDefault(), null,
                Base64Variants.defaultVariant, DefaultCacheProvider.DEFAULT, CirJsonNodeFactory.instance, null)

        val DEFAULT_TYPE_RESOLVER_PROVIDER = TypeResolverProvider()

        val NO_ABSTRACT_TYPE_RESOLVERS = emptyArray<AbstractTypeResolver>()

    }

}