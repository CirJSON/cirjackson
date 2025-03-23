package org.cirjson.cirjackson.annotations

import kotlin.reflect.full.memberProperties
import kotlin.test.*

class CirJsonAutoDetectTest {

    @Test
    fun testAnnotationProperties() {
        val m = Bogus::class.memberProperties.find { it.name == "value" }!!

        assertTrue(CirJsonAutoDetect.Visibility.ANY.isVisible(m))
        assertFalse(CirJsonAutoDetect.Visibility.NONE.isVisible(m))

        assertTrue(CirJsonAutoDetect.Visibility.NON_PRIVATE.isVisible(m))
        assertTrue(CirJsonAutoDetect.Visibility.PUBLIC_ONLY.isVisible(m))
        assertTrue(CirJsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC.isVisible(m))

        assertFalse(CirJsonAutoDetect.Visibility.DEFAULT.isVisible(m))
    }

    @Test
    fun testBasicValueProperties() {
        assertEquals(CirJsonAutoDetect::class, DEFAULT.valueFor())
        assertNotEquals(0, DEFAULT.hashCode())

        assertEquals(DEFAULT, DEFAULT)
        assertNotEquals(null, DEFAULT as Any?)
        assertNotEquals("foo", DEFAULT as Any)
    }

    @Test
    fun testEquality() {
        assertEquals(NO_OVERRIDES, NO_OVERRIDES)
        assertEquals(DEFAULT, DEFAULT)
        assertNotEquals(DEFAULT, NO_OVERRIDES)
        assertNotEquals(NO_OVERRIDES, DEFAULT)
    }

    @Test
    fun testFromAnnotation() {
        val annotation = Custom::class.annotations.find { it is CirJsonAutoDetect } as CirJsonAutoDetect
        val value1 = CirJsonAutoDetect.Value.from(annotation)
        val value2 = CirJsonAutoDetect.Value.from(annotation)
        assertNotSame(value1, value2)
        assertEquals(value1, value2)
        assertEquals(value2, value1)

        assertEquals(annotation.fieldVisibility, value1.fieldVisibility)
        assertEquals(annotation.getterVisibility, value1.getterVisibility)
        assertEquals(annotation.isGetterVisibility, value1.isGetterVisibility)
        assertEquals(annotation.setterVisibility, value1.setterVisibility)
        assertEquals(annotation.creatorVisibility, value1.creatorVisibility)
        assertEquals(annotation.scalarConstructorVisibility, value1.scalarConstructorVisibility)
    }

    @Test
    fun testToString() {
        assertEquals(
                "CirJsonAutoDetect.Value(fieldVisibility=PUBLIC_ONLY,getterVisibility=PUBLIC_ONLY,isGetterVisibility=PUBLIC_ONLY,setterVisibility=ANY,creatorVisibility=PUBLIC_ONLY,scalarConstructorVisibility=NON_PRIVATE)",
                DEFAULT.toString())
        assertEquals(
                "CirJsonAutoDetect.Value(fieldVisibility=DEFAULT,getterVisibility=DEFAULT,isGetterVisibility=DEFAULT,setterVisibility=DEFAULT,creatorVisibility=DEFAULT,scalarConstructorVisibility=DEFAULT)",
                NO_OVERRIDES.toString())
    }

    @Test
    fun testSimpleMerge() {
        val base = CirJsonAutoDetect.Value.construct(CirJsonAutoDetect.Visibility.ANY,
                CirJsonAutoDetect.Visibility.PUBLIC_ONLY, CirJsonAutoDetect.Visibility.ANY,
                CirJsonAutoDetect.Visibility.NONE, CirJsonAutoDetect.Visibility.ANY,
                CirJsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC)
        val overrides = CirJsonAutoDetect.Value.construct(CirJsonAutoDetect.Visibility.NON_PRIVATE,
                CirJsonAutoDetect.Visibility.DEFAULT, CirJsonAutoDetect.Visibility.PUBLIC_ONLY,
                CirJsonAutoDetect.Visibility.DEFAULT, CirJsonAutoDetect.Visibility.DEFAULT,
                CirJsonAutoDetect.Visibility.PUBLIC_ONLY)
        var merged = CirJsonAutoDetect.Value.merge(base, overrides)!!
        assertNotEquals(merged, base)
        assertNotEquals(merged, overrides)
        assertEquals(merged, merged)

        assertEquals(CirJsonAutoDetect.Visibility.NON_PRIVATE, merged.fieldVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.PUBLIC_ONLY, merged.getterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.PUBLIC_ONLY, merged.isGetterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.NONE, merged.setterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.ANY, merged.creatorVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.PUBLIC_ONLY, merged.scalarConstructorVisibility)

        merged = CirJsonAutoDetect.Value.merge(overrides, base)!!
        assertEquals(CirJsonAutoDetect.Visibility.ANY, merged.fieldVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.PUBLIC_ONLY, merged.getterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.ANY, merged.isGetterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.NONE, merged.setterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.ANY, merged.creatorVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC, merged.scalarConstructorVisibility)

        assertSame(overrides, CirJsonAutoDetect.Value.merge(null, overrides))
        assertSame(overrides, CirJsonAutoDetect.Value.merge(overrides, null))
    }

    @Test
    fun testFactoryMethods() {
        val value1 = CirJsonAutoDetect.Value.construct(PropertyAccessor.FIELD, CirJsonAutoDetect.Visibility.ANY)
        assertEquals(CirJsonAutoDetect.Visibility.ANY, value1.fieldVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.DEFAULT, value1.getterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.DEFAULT, value1.isGetterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.DEFAULT, value1.setterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.DEFAULT, value1.creatorVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.DEFAULT, value1.scalarConstructorVisibility)

        val value2 = CirJsonAutoDetect.Value.construct(PropertyAccessor.ALL, CirJsonAutoDetect.Visibility.NONE)
        assertEquals(CirJsonAutoDetect.Visibility.NONE, value2.fieldVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.NONE, value2.getterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.NONE, value2.isGetterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.NONE, value2.setterVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.NONE, value2.creatorVisibility)
        assertEquals(CirJsonAutoDetect.Visibility.NONE, value2.scalarConstructorVisibility)
    }

    @Test
    fun testSimpleChanges() {
        assertSame(NO_OVERRIDES, NO_OVERRIDES.withFieldVisibility(CirJsonAutoDetect.Visibility.DEFAULT))
        var value = NO_OVERRIDES.withCreatorVisibility(CirJsonAutoDetect.Visibility.PUBLIC_ONLY)
        assertNotSame(NO_OVERRIDES, value)
        assertEquals(CirJsonAutoDetect.Visibility.PUBLIC_ONLY, value.creatorVisibility)

        value = NO_OVERRIDES.withFieldVisibility(CirJsonAutoDetect.Visibility.PUBLIC_ONLY)
        assertEquals(CirJsonAutoDetect.Visibility.PUBLIC_ONLY, value.fieldVisibility)

        value = NO_OVERRIDES.withGetterVisibility(CirJsonAutoDetect.Visibility.PUBLIC_ONLY)
        assertEquals(CirJsonAutoDetect.Visibility.PUBLIC_ONLY, value.getterVisibility)

        value = NO_OVERRIDES.withIsGetterVisibility(CirJsonAutoDetect.Visibility.PUBLIC_ONLY)
        assertEquals(CirJsonAutoDetect.Visibility.PUBLIC_ONLY, value.isGetterVisibility)

        value = NO_OVERRIDES.withSetterVisibility(CirJsonAutoDetect.Visibility.PUBLIC_ONLY)
        assertEquals(CirJsonAutoDetect.Visibility.PUBLIC_ONLY, value.setterVisibility)

        value = NO_OVERRIDES.withScalarConstructorVisibility(CirJsonAutoDetect.Visibility.PUBLIC_ONLY)
        assertEquals(CirJsonAutoDetect.Visibility.PUBLIC_ONLY, value.scalarConstructorVisibility)
    }

    private class Bogus {

        var value = ""

    }

    @CirJsonAutoDetect(fieldVisibility = CirJsonAutoDetect.Visibility.NON_PRIVATE,
            getterVisibility = CirJsonAutoDetect.Visibility.PROTECTED_AND_PUBLIC,
            isGetterVisibility = CirJsonAutoDetect.Visibility.NONE,
            setterVisibility = CirJsonAutoDetect.Visibility.PUBLIC_ONLY,
            creatorVisibility = CirJsonAutoDetect.Visibility.ANY)
    private class Custom

    companion object {

        val DEFAULT = CirJsonAutoDetect.Value.DEFAULT

        val NO_OVERRIDES = CirJsonAutoDetect.Value.NO_OVERRIDES

    }

}