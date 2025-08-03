package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.*
import org.cirjson.cirjackson.core.type.TypeReference
import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.PropertyName
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.cirjsontype.TypeResolverProvider
import org.cirjson.cirjackson.databind.introspection.*
import org.cirjson.cirjackson.databind.type.TypeFactory
import org.cirjson.cirjackson.databind.util.RootNameLookup
import kotlin.reflect.KClass

abstract class MapperConfigBase<CFG : ConfigFeature, T : MapperConfigBase<CFG, T>> : MapperConfig<T> {

    /*
     *******************************************************************************************************************
     * Construction
     *******************************************************************************************************************
     */

    protected constructor(builder: MapperBuilder<*, *>, mapperFeatures: Long, typeFactory: TypeFactory,
            classIntrospector: ClassIntrospector, mixins: MixInHandler, subtypeResolver: SubtypeResolver,
            configOverrides: ConfigOverrides, defaultAttributes: ContextAttributes, rootNames: RootNameLookup) : super(
            builder.baseSettings(), mapperFeatures) {
    }

    /*
     *******************************************************************************************************************
     * Simple factory access, related
     *******************************************************************************************************************
     */

    final override val typeFactory: TypeFactory
        get() = TODO("Not yet implemented")

    override fun classIntrospectorInstance(): ClassIntrospector {
        TODO("Not yet implemented")
    }

    override val typeResolverProvider: TypeResolverProvider
        get() = TODO("Not yet implemented")

    final override val subtypeResolver: SubtypeResolver
        get() = TODO("Not yet implemented")

    final override fun constructType(clazz: KClass<*>): KotlinType {
        TODO("Not yet implemented")
    }

    final override fun constructType(valueTypeReference: TypeReference<*>): KotlinType {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Simple feature access
     *******************************************************************************************************************
     */

    final override fun isEnabled(feature: DatatypeFeature): Boolean {
        TODO("Not yet implemented")
    }

    final override val datatypeFeatures: DatatypeFeatures
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Simple config property access
     *******************************************************************************************************************
     */

    final override val activeView: KClass<*>?
        get() = TODO("Not yet implemented")

    final override val attributes: ContextAttributes
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Configuration access; default/overrides
     *******************************************************************************************************************
     */

    final override fun getConfigOverride(type: KClass<*>): ConfigOverride {
        TODO("Not yet implemented")
    }

    final override fun findConfigOverride(type: KClass<*>): ConfigOverride? {
        TODO("Not yet implemented")
    }

    final override val defaultPropertyInclusion: CirJsonInclude.Value?
        get() = TODO("Not yet implemented")

    final override fun getDefaultPropertyInclusion(baseType: KClass<*>): CirJsonInclude.Value? {
        TODO("Not yet implemented")
    }

    final override fun getDefaultInclusion(baseType: KClass<*>, propertyType: KClass<*>): CirJsonInclude.Value {
        TODO("Not yet implemented")
    }

    final override fun getDefaultPropertyFormat(baseType: KClass<*>): CirJsonFormat.Value {
        TODO("Not yet implemented")
    }

    final override fun getDefaultPropertyIgnorals(baseType: KClass<*>): CirJsonIgnoreProperties.Value? {
        TODO("Not yet implemented")
    }

    final override fun getDefaultPropertyIgnorals(baseType: KClass<*>,
            actualClass: AnnotatedClass): CirJsonIgnoreProperties.Value? {
        TODO("Not yet implemented")
    }

    final override fun getDefaultPropertyInclusions(baseType: KClass<*>,
            actualClass: AnnotatedClass): CirJsonIncludeProperties.Value? {
        TODO("Not yet implemented")
    }

    final override val defaultVisibilityChecker: VisibilityChecker
        get() = TODO("Not yet implemented")

    final override fun getDefaultVisibilityChecker(baseType: KClass<*>,
            actualClass: AnnotatedClass): VisibilityChecker {
        TODO("Not yet implemented")
    }

    final override val defaultNullHandling: CirJsonSetter.Value
        get() = TODO("Not yet implemented")

    override val defaultMergeable: Boolean?
        get() = TODO("Not yet implemented")

    override fun getDefaultMergeable(baseType: KClass<*>): Boolean? {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Other config access
     *******************************************************************************************************************
     */

    override fun findRootName(context: DatabindContext, rootType: KotlinType): PropertyName {
        TODO("Not yet implemented")
    }

    override fun findRootName(context: DatabindContext, rawRootType: KClass<*>): PropertyName {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * MixInResolver implementation
     *******************************************************************************************************************
     */

    final override fun findMixInClassFor(kClass: KClass<*>): KClass<*> {
        TODO("Not yet implemented")
    }

    override fun hasMixIns(): Boolean {
        TODO("Not yet implemented")
    }

    override fun snapshot(): MixInResolver {
        TODO("Not yet implemented")
    }

}