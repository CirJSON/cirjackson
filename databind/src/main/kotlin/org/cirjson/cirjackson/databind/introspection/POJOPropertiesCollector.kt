package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.AnnotationIntrospector
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig

open class POJOPropertiesCollector protected constructor(protected val myConfig: MapperConfig<*>,
        protected val myForSerialization: Boolean, protected val myType: KotlinType,
        protected val myClassDefinition: AnnotatedClass, protected val myAccessorNaming: AccessorNamingStrategy) {

    /*
     *******************************************************************************************************************
     * Public API
     *******************************************************************************************************************
     */

    open val config: MapperConfig<*>
        get() = TODO("Not yet implemented")

    open val type: KotlinType
        get() = TODO("Not yet implemented")

    open val classDefinition: AnnotatedClass
        get() = TODO("Not yet implemented")

    open val annotationIntrospector: AnnotationIntrospector
        get() = TODO("Not yet implemented")

    open val properties: MutableList<BeanPropertyDefinition>
        get() = TODO("Not yet implemented")

    open val cirJsonKeyAccessor: AnnotatedMember?
        get() = TODO("Not yet implemented")

    open val injectables: Map<Any, AnnotatedMember>?
        get() = TODO("Not yet implemented")

    open val potentialCreators: PotentialCreators
        get() = TODO("Not yet implemented")

    open val cirJsonValueAccessor: AnnotatedMember?
        get() = TODO("Not yet implemented")

    open val anyGetterField: AnnotatedMember?
        get() = TODO("Not yet implemented")

    open val anyGetterMethod: AnnotatedMember?
        get() = TODO("Not yet implemented")

    open val anySetterField: AnnotatedMember?
        get() = TODO("Not yet implemented")

    open val anySetterMethod: AnnotatedMethod?
        get() = TODO("Not yet implemented")

    open val ignoredPropertyNames: Set<String>?
        get() = TODO("Not yet implemented")

    open val objectIdInfo: ObjectIdInfo
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Internal methods; removing ignored properties
     *******************************************************************************************************************
     */

    protected open fun collectIgnorals(name: String?) {
        TODO("Not yet implemented")
    }

    /**
     * An internal accessor for [collectIgnorals]
     */
    internal fun internalCollectIgnorals(name: String?) {
        collectIgnorals(name)
    }

    companion object {

        internal fun create(config: MapperConfig<*>, forSerialization: Boolean, type: KotlinType,
                classDefinition: AnnotatedClass, accessorNaming: AccessorNamingStrategy): POJOPropertiesCollector {
            return POJOPropertiesCollector(config, forSerialization, type, classDefinition, accessorNaming)
        }

    }

}