package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.databind.util.FullyNamed

open class PropertyName(simpleName: String?, val namespace: String?) : FullyNamed {

    constructor(simpleName: String?) : this(simpleName, null)

    /*
     *******************************************************************************************************************
     * FullyNamed implementation
     *******************************************************************************************************************
     */

    override val name: String
        get() = TODO("Not yet implemented")

    override val fullName: PropertyName
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Accessors
     *******************************************************************************************************************
     */

    open val simpleName: String
        get() = TODO("Not yet implemented")

    open fun hasSimpleName(): Boolean {
        TODO("Not yet implemented")
    }

    open fun hasSimpleName(string: String?): Boolean {
        TODO("Not yet implemented")
    }

    open fun hasNamespace(): Boolean {
        TODO("Not yet implemented")
    }

    open fun isEmpty(): Boolean {
        TODO("Not yet implemented")
    }

    companion object {

        private const val USE_DEFAULT_STRING = ""

        private const val NO_NAME_STRING = ""

        val USE_DEFAULT = PropertyName(USE_DEFAULT_STRING, null)

        val NO_NAME = PropertyName(String(NO_NAME_STRING.toByteArray()), null)

        fun construct(simpleName: String?): PropertyName {
            TODO("Not yet implemented")
        }

        fun construct(simpleName: String?, namespace: String?): PropertyName {
            TODO("Not yet implemented")
        }

        fun merge(name1: PropertyName?, name2: PropertyName?): PropertyName? {
            TODO("Not yet implemented")
        }

    }

}