package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.cirjsontype.SubtypeResolver
import org.cirjson.cirjackson.databind.introspection.ClassIntrospector
import org.cirjson.cirjackson.databind.introspection.MixInHandler
import org.cirjson.cirjackson.databind.introspection.MixInResolver
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
     * Configuration access; default/overrides
     *******************************************************************************************************************
     */

    override fun getDefaultInclusion(baseType: KClass<*>, propertyType: KClass<*>): CirJsonInclude.Value {
        TODO("Not yet implemented")
    }

    override fun getDefaultPropertyFormat(baseType: KClass<*>): CirJsonFormat.Value {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * MixInResolver implementation
     *******************************************************************************************************************
     */

    override fun findMixInClassFor(kClass: KClass<*>): KClass<*> {
        TODO("Not yet implemented")
    }

    override fun hasMixIns(): Boolean {
        TODO("Not yet implemented")
    }

    override fun snapshot(): MixInResolver {
        TODO("Not yet implemented")
    }

}