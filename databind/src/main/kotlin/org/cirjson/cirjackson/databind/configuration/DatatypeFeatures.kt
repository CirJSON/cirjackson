package org.cirjson.cirjackson.databind.configuration

open class DatatypeFeatures protected constructor(private val myEnabledFor1: Int, private val myEnabledFor2: Int,
        private val myExplicitFor1: Int, private val myExplicitFor2: Int) {

    companion object {

        const val FEATURE_INDEX_ENUM = 0

        const val FEATURE_INDEX_CIRJSON_NODE = 1

    }

}