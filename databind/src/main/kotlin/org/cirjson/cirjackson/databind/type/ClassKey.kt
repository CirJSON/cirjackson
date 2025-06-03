package org.cirjson.cirjackson.databind.type

import kotlin.reflect.KClass

class ClassKey(private var myClass: KClass<*>?) : Comparable<ClassKey> {

    override operator fun compareTo(other: ClassKey): Int {
        TODO("Not yet implemented")
    }

}