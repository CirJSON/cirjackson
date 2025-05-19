package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.CirJsonFormat
import org.cirjson.cirjackson.annotations.CirJsonInclude
import org.cirjson.cirjackson.databind.introspection.MixInResolver
import kotlin.reflect.KClass

abstract class MapperConfigBase<CFG : ConfigFeature, T : MapperConfigBase<CFG, T>> : MapperConfig<T>() {

    override fun getDefaultInclusion(baseType: KClass<*>, propertyType: KClass<*>): CirJsonInclude.Value {
        TODO("Not yet implemented")
    }

    override fun getDefaultPropertyFormat(baseType: KClass<*>): CirJsonFormat.Value {
        TODO("Not yet implemented")
    }

    override fun snapshot(): MixInResolver {
        TODO("Not yet implemented")
    }

}