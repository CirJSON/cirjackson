package org.cirjson.cirjackson.annotations

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PropertyAccessorTest {

    @Test
    fun testProperties() {
        assertTrue(PropertyAccessor.ALL.isCreatorEnabled)
        assertTrue(PropertyAccessor.CREATOR.isCreatorEnabled)

        assertTrue(PropertyAccessor.ALL.isGetterEnabled)
        assertTrue(PropertyAccessor.GETTER.isGetterEnabled)
        assertFalse(PropertyAccessor.CREATOR.isGetterEnabled)

        assertTrue(PropertyAccessor.ALL.isIsGetterEnabled)
        assertTrue(PropertyAccessor.IS_GETTER.isIsGetterEnabled)
        assertFalse(PropertyAccessor.CREATOR.isIsGetterEnabled)

        assertTrue(PropertyAccessor.ALL.isSetterEnabled)
        assertTrue(PropertyAccessor.SETTER.isSetterEnabled)
        assertFalse(PropertyAccessor.CREATOR.isSetterEnabled)

        assertTrue(PropertyAccessor.ALL.isFieldEnabled)
        assertTrue(PropertyAccessor.FIELD.isFieldEnabled)
        assertFalse(PropertyAccessor.CREATOR.isFieldEnabled)
    }

}