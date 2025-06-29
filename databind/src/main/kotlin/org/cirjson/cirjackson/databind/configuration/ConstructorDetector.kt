package org.cirjson.cirjackson.databind.configuration

class ConstructorDetector private constructor(private val mySingleArgMode: SingleArgConstructor,
        private val myRequireCtorAnnotation: Boolean, private val myAllowJDKTypeCtors: Boolean) {

    private constructor(singleArgMode: SingleArgConstructor) : this(singleArgMode, false, false)

    enum class SingleArgConstructor {

        HEURISTIC,

    }

    companion object {

        val DEFAULT = ConstructorDetector(SingleArgConstructor.HEURISTIC)

    }

}