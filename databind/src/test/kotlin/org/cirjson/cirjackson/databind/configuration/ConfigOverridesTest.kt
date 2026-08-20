package org.cirjson.cirjackson.databind.configuration

import org.cirjson.cirjackson.annotations.CirJsonAutoDetect
import org.cirjson.cirjackson.annotations.PropertyAccessor
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigOverridesTest {

    @Test
    fun testSnapshot() {
        val configOverrides = ConfigOverrides()
        configOverrides.findOrCreateOverride(String::class).setVisibility(
                CirJsonAutoDetect.Value.construct(PropertyAccessor.SETTER, CirJsonAutoDetect.Visibility.NONE))

        assertEquals(configOverrides.toString(), configOverrides.snapshot().toString())
    }

}