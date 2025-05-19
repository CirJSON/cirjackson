package org.cirjson.cirjackson.databind

import org.cirjson.cirjackson.databind.util.FullyNamed

open class PropertyName(simpleName: String?, val namespace: String?) : FullyNamed {

    override val name: String
        get() = TODO("Not yet implemented")

    override val fullName: PropertyName
        get() = TODO("Not yet implemented")

    open val simpleName: String
        get() = TODO("Not yet implemented")

    companion object {

        private const val NO_NAME_STRING = ""

        val NO_NAME = PropertyName(String(NO_NAME_STRING.toByteArray()), null)

    }

}