package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.databind.introspection.MixInResolver

abstract class MapperConfigBase<CFG : ConfigFeature, T : MapperConfigBase<CFG, T>> : MapperConfig<T>() {

    override fun snapshot(): MixInResolver {
        TODO("Not yet implemented")
    }

}