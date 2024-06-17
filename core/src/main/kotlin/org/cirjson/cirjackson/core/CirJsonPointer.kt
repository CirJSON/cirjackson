package org.cirjson.cirjackson.core

/**
 * Pointer instances can be used to locate logical CirJSON nodes for things like
 * tree traversal (see [TreeNode.at]).
 *
 * Instances are fully immutable and can be cached, shared between threads.
 */
class CirJsonPointer {

    companion object {

        /**
         * Factory method that will construct a pointer instance that describes path to location given
         * [TokenStreamContext] points to.
         *
         * @param streamContext Context to build pointer expression fot
         *
         * @param includeRoot Whether to include number offset for virtual "root context" or not.
         *
         * @return [CirJsonPointer] path to location of given context
         */
        fun forPath(streamContext: TokenStreamContext?, includeRoot: Boolean): CirJsonPointer {
            var context = streamContext
            TODO()
        }

    }

}