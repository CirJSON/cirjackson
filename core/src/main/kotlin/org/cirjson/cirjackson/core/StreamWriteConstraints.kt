package org.cirjson.cirjackson.core

open class StreamWriteConstraints protected constructor() {

    companion object {

        private val DEFAULT = StreamWriteConstraints()

        private var CURRENT_DEFAULT = DEFAULT

        fun defaults() = CURRENT_DEFAULT

    }

}