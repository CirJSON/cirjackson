package org.cirjson.cirjackson.databind.type

import org.cirjson.cirjackson.databind.KotlinType
import java.lang.reflect.Type

/**
 * Class that defines API that can be used to modify details of [KotlinType] instances constructed using [TypeFactory].
 * Registered modifiers are called in order, to let them modify (or replace) basic type instance factory constructs.
 * This is typically needed to support creation of [MapLikeType] and [CollectionLikeType] instances, as those cannot be
 * constructed in generic fashion.
 */
abstract class TypeModifier {

    /**
     * Method called to let modifier change constructed type definition. Note that this is only guaranteed to be called
     * for non-container types ("simple" types not recognized as arrays, `Collection` or `Map`).
     *
     * @param type Instance to modify
     *
     * @param jdkType JDK type that was used to construct instance to modify
     *
     * @param context Type resolution context used for the type
     *
     * @param typeFactory Type factory that can be used to construct parameter type; note, however, that care must be
     * taken to avoid infinite loops -- specifically, do not construct instance of primary type itself
     *
     * @return Actual type instance to use; usually either `type` (as is or with modifications), or a newly constructed
     * type instance based on it. Cannot be `null`.
     */
    abstract fun modifyType(type: KotlinType, jdkType: Type, context: TypeBindings,
            typeFactory: TypeFactory): KotlinType
    
}