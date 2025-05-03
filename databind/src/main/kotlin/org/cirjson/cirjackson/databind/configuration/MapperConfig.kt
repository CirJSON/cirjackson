package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.databind.introspection.MixInResolver

abstract class MapperConfig<T : MapperConfig<T>> : MixInResolver {
}