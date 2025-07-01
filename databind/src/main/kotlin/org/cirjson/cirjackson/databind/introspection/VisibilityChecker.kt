package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.annotations.CirJsonAutoDetect

open class VisibilityChecker {

    companion object {

        val DEFAULT: VisibilityChecker = TODO("Not yet implemented")

        val ALL_PUBLIC_EXCEPT_CREATORS: VisibilityChecker = TODO("Not yet implemented")

        fun construct(visibility: CirJsonAutoDetect.Value?): VisibilityChecker {
            TODO("Not yet implemented")
        }

    }

}