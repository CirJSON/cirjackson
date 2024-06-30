package org.cirjson.cirjackson.core.type

import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResolvedTypeTest : TestBase() {

    @Test
    fun testResolvedType() {
        val type1 = MockResolvedType(false);
        assertFalse(type1.isReferenceType);
        val type2 = MockResolvedType(true);
        assertTrue(type2.isReferenceType);
    }

    class MockResolvedType(private val isSelfReferencedType: Boolean) : ResolvedType() {

        override val referencedType: ResolvedType?
            get() = if (isSelfReferencedType) this else null

        override val rawClass: Class<*>
            get() = javaClass

        override fun hasRawClass(clazz: Class<*>): Boolean {
            return false
        }

        override val isAbstract: Boolean
            get() = false

        override val isConcrete: Boolean
            get() = false

        override val isThrowable: Boolean
            get() = false

        override val isArrayType: Boolean
            get() = false

        override val isEnumType: Boolean
            get() = false

        override val isInterface: Boolean
            get() = false

        override val isPrimitive: Boolean
            get() = false

        override val isFinal: Boolean
            get() = false

        override val isContainerType: Boolean
            get() = false

        override val isCollectionLikeType: Boolean
            get() = false

        override val hasGenericTypes: Boolean
            get() = false

        override val keyType: ResolvedType?
            get() = null

        override val contentType: ResolvedType?
            get() = null

        override fun containedTypeCount(): Int {
            return -1
        }

        override fun containedType(index: Int): ResolvedType? {
            return null
        }

    }

}