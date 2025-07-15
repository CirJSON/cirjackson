package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.annotation.CirJsonPOJOBuilder
import org.cirjson.cirjackson.databind.configuration.MapperConfig

open class DefaultAccessorNamingStrategy protected constructor(protected val myConfig: MapperConfig<*>,
        protected val myForClass: AnnotatedClass, protected val myMutatorPrefix: String?,
        protected val myGetterPrefix: String?, protected val myISGetterPrefix: String?,
        protected val myBaseNameValidator: BaseNameValidator?) : AccessorNamingStrategy() {

    interface BaseNameValidator {
    }

    /*
     *******************************************************************************************************************
     * Standard Provider implementation
     *******************************************************************************************************************
     */

    open class Provider protected constructor(protected val mySetterPrefix: String?,
            protected val myWithPrefix: String?, protected val myGetterPrefix: String?,
            protected val myIsGetterPrefix: String?, protected val myBaseNameValidator: BaseNameValidator?) :
            AccessorNamingStrategy.Provider() {

        constructor() : this("set", CirJsonPOJOBuilder.DEFAULT_WITH_PREFIX, "get", "is", null)

    }

}