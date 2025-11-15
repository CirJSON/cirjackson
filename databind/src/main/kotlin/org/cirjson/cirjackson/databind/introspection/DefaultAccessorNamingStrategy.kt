package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.BeanDescription
import org.cirjson.cirjackson.databind.annotation.CirJsonPOJOBuilder
import org.cirjson.cirjackson.databind.configuration.MapperConfig

open class DefaultAccessorNamingStrategy protected constructor(protected val myConfig: MapperConfig<*>,
        protected val myForClass: AnnotatedClass, protected val myMutatorPrefix: String?,
        protected val myGetterPrefix: String?, protected val myISGetterPrefix: String?,
        protected val myBaseNameValidator: BaseNameValidator?) : AccessorNamingStrategy() {

    override fun findNameForIsGetter(method: AnnotatedMethod, name: String): String? {
        TODO("Not yet implemented")
    }

    override fun findNameForRegularGetter(method: AnnotatedMethod, name: String): String? {
        TODO("Not yet implemented")
    }

    override fun findNameForMutator(method: AnnotatedMethod, name: String): String? {
        TODO("Not yet implemented")
    }

    override fun modifyFieldName(field: AnnotatedField, name: String): String? {
        TODO("Not yet implemented")
    }

    fun interface BaseNameValidator {

        fun accept(firstChar: Char, baseName: String, offset: Int): Boolean

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

        override fun forPOJO(config: MapperConfig<*>, valueClass: AnnotatedClass): AccessorNamingStrategy {
            TODO("Not yet implemented")
        }

        override fun forBuilder(config: MapperConfig<*>, builderClass: AnnotatedClass,
                valueTypeDescription: BeanDescription): AccessorNamingStrategy {
            TODO("Not yet implemented")
        }

        override fun forRecord(config: MapperConfig<*>, recordClass: AnnotatedClass): AccessorNamingStrategy {
            TODO("Not yet implemented")
        }

    }

}