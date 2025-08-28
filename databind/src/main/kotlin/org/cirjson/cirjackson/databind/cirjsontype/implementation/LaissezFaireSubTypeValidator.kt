package org.cirjson.cirjackson.databind.cirjsontype.implementation

import org.cirjson.cirjackson.databind.DatabindContext
import org.cirjson.cirjackson.databind.KotlinType
import org.cirjson.cirjackson.databind.cirjsontype.PolymorphicTypeValidator

/**
 * Simple [PolymorphicTypeValidator] implementation used by [StandardTypeResolverBuilder] in cases where all subtypes
 * for given base type are deemed acceptable; usually because user controls base type in question (and no serialization
 * gadgets should exist).
 *
 * This implementation is NOT available to regular users as its use can easily open up security holes. Only used
 * internally in cases where validation results from regular implementation indicate that no further checks are needed.
 */
internal object LaissezFaireSubTypeValidator : PolymorphicTypeValidator.Base() {

    override fun validateBaseType(context: DatabindContext, baseType: KotlinType): Validity {
        return Validity.INDETERMINATE
    }

    override fun validateSubClassName(context: DatabindContext, baseType: KotlinType, subClassName: String): Validity {
        return Validity.ALLOWED
    }

    override fun validateSubtype(context: DatabindContext, baseType: KotlinType, subType: KotlinType): Validity {
        return Validity.ALLOWED
    }

}