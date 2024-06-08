package org.cirjson.cirjackson.core.util

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.TokenStreamFactory

/**
 * Simple interface to allow adding decorators around [CirJsonGenerator]s.
 */
fun interface CirJsonGeneratorDecorator {

    /**
     * Allow to decorate [CirJsonGenerator] instances returned by [TokenStreamFactory].
     *
     * @param factory The factory which was used to build the original generator
     *
     * @param generator The generator to decorate. This might already be a decorated instance, not the original.
     *
     * @return decorated generator
     */
    fun generate(factory: TokenStreamFactory, generator: CirJsonGenerator): CirJsonGenerator

}