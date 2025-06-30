package org.cirjson.cirjackson.databind.configuration

open class CoercionConfig {

    var acceptBlankAsEmpty: Boolean?
        protected set

    protected val myCoercionsByShape: Array<CoercionAction?>

    constructor() {
        myCoercionsByShape = arrayOfNulls(INPUT_SHAPE_COUNT)
        acceptBlankAsEmpty = null
    }

    protected constructor(source: CoercionConfig) {
        acceptBlankAsEmpty = source.acceptBlankAsEmpty
        myCoercionsByShape = source.myCoercionsByShape.copyOf()
    }

    fun findAction(shape: CoercionInputShape): CoercionAction? {
        return myCoercionsByShape[shape.ordinal]
    }

    companion object {

        private val INPUT_SHAPE_COUNT = CoercionInputShape.entries.size

    }

}