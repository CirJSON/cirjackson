package org.cirjson.cirjackson.databind.introspection

import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.configuration.MapperConfig
import org.cirjson.cirjackson.databind.type.TypeBindings
import org.cirjson.cirjackson.databind.util.Annotations
import kotlin.reflect.KClass
import kotlin.reflect.KType

class AnnotatedClass : Annotated, TypeResolutionContext {

    /*
     *******************************************************************************************************************
     * Lifecycle
     *******************************************************************************************************************
     */

    internal constructor(config: MapperConfig<*>?, type: KotlinType?, clazz: KClass<*>, supertypes: List<KotlinType>,
            primaryMixIn: KClass<*>?, annotations: Annotations, bindings: TypeBindings, mixInResolver: MixInResolver?,
            collectAnnotations: Boolean) : super() {
    }

    /*
     *******************************************************************************************************************
     * TypeResolutionContext implementation
     *******************************************************************************************************************
     */

    override fun resolveType(type: KType): KotlinType {
        TODO("Not yet implemented")
    }

    /*
     *******************************************************************************************************************
     * Annotated implementation
     *******************************************************************************************************************
     */

    override val annotated: KClass<*>
        get() = TODO("Not yet implemented")

    override val modifiers: Int
        get() = TODO("Not yet implemented")

    override val name: String
        get() = TODO("Not yet implemented")

    override fun <A : Annotation> getAnnotation(kClass: KClass<A>): A? {
        TODO("Not yet implemented")
    }

    override fun hasAnnotation(kClass: KClass<*>): Boolean {
        TODO("Not yet implemented")
    }

    override fun hasOneOf(annotationClasses: Array<KClass<out Annotation>>): Boolean {
        TODO("Not yet implemented")
    }

    override val rawType: KClass<*>
        get() = TODO("Not yet implemented")

    override val type: KotlinType
        get() = TODO("Not yet implemented")

    /*
     *******************************************************************************************************************
     * Standard method overrides
     *******************************************************************************************************************
     */

    override fun toString(): String {
        TODO("Not yet implemented")
    }

    override fun hashCode(): Int {
        TODO("Not yet implemented")
    }

    override fun equals(other: Any?): Boolean {
        TODO("Not yet implemented")
    }

}