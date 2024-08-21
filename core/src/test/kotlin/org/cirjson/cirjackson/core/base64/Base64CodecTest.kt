package org.cirjson.cirjackson.core.base64

import org.cirjson.cirjackson.core.Base64Variant
import org.cirjson.cirjackson.core.Base64Variants
import org.cirjson.cirjackson.core.TestBase
import kotlin.test.*

class Base64CodecTest : TestBase() {

    @Test
    fun testVariantAccess() {
        for (variant in BASE64_VARIANTS) {
            assertSame(variant, Base64Variants.valueOf(variant.name))
        }

        try {
            Base64Variants.valueOf("foobar")
        } catch (e: IllegalArgumentException) {
            verifyException(e, "No Base64Variant with name 'foobar'")
        }
    }

    @Test
    fun testProps() {
        val variant = Base64Variants.MIME
        assertEquals("MIME", variant.name)
        assertEquals("MIME", variant.toString())
        assertTrue(variant.isUsingPadding)
        assertFalse(variant.usesPaddingChar('X'))
        assertFalse(variant.usesPaddingChar('X'.code))
        assertTrue(variant.usesPaddingChar('='))
        assertTrue(variant.usesPaddingChar('='.code))
        assertEquals('='.code.toByte(), variant.paddingByte)
        assertEquals(76, variant.maxLineLength)
    }

    @Test
    fun testCharEncoding() {
        val variant = Base64Variants.MIME

        assertEquals(Base64Variant.BASE64_VALUE_INVALID, variant.decodeBase64Char('?'))
        assertEquals(Base64Variant.BASE64_VALUE_INVALID, variant.decodeBase64Char('?'.code))
        assertEquals(Base64Variant.BASE64_VALUE_INVALID, variant.decodeBase64Char(0xA0))
        assertEquals(Base64Variant.BASE64_VALUE_INVALID, variant.decodeBase64Char(0xA0.toChar()))

        assertEquals(Base64Variant.BASE64_VALUE_INVALID, variant.decodeBase64Byte('?'.code.toByte()))
        assertEquals(Base64Variant.BASE64_VALUE_INVALID, variant.decodeBase64Byte(0xA0.toByte()))

        assertEquals(0, variant.decodeBase64Char('A'))
        assertEquals(1, variant.decodeBase64Char('B'.code))
        assertEquals(2, variant.decodeBase64Char('C'.code.toByte().toInt()))

        assertEquals(0, variant.decodeBase64Byte('A'.code.toByte()))
        assertEquals(1, variant.decodeBase64Byte('B'.code.toByte()))
        assertEquals(2, variant.decodeBase64Byte('C'.code.toByte()))

        assertEquals('/', variant.encodeBase64BitsAsChar(63))
        assertEquals('b'.code.toByte(), variant.encodeBase64BitsAsByte(27))

        val expectedString = "HwdJ"
        val triplet = 0X1F0749
        val stringBuilder = StringBuilder()
        variant.encodeBase64Chunk(stringBuilder, triplet)
        assertEquals(expectedString, stringBuilder.toString())

        val expected = expectedString.toByteArray(Charsets.UTF_8)
        val actual = ByteArray(expected.size)
        variant.encodeBase64Chunk(triplet, actual, 0)
        assertContentEquals(expected, actual)
    }

    @Test
    fun testConvenienceMethods() {
        val variant = Base64Variants.MIME

        val input = byteArrayOf(1, 2, 34, 127, -1)
        val encoded = variant.encode(input, false)
        var decoded = variant.decode(encoded)
        assertContentEquals(input, decoded)

        assertEquals(quote(encoded), variant.encode(input, true))

        decoded = variant.decode("\n$encoded")
        assertContentEquals(input, decoded)
        decoded = variant.decode("   $encoded")
        assertContentEquals(input, decoded)
        decoded = variant.decode("$encoded   ")
        assertContentEquals(input, decoded)
        decoded = variant.decode("$encoded\n")
        assertContentEquals(input, decoded)
    }

    @Test
    fun testConvenienceMethodWithLFs() {
        val variant = Base64Variants.MIME

        val data = ByteArray(100) { 1 }

        val stringBuilder = StringBuilder(140)

        for (i in 0..<100 / 3) {
            stringBuilder.append("AQEB")

            if (stringBuilder.length == 76) {
                stringBuilder.append("##")
            }
        }

        stringBuilder.append("AQ==")
        val expected = stringBuilder.toString()

        assertEquals(expected.replace("##", "\\n"), variant.encode(data, false))

        assertEquals(expected.replace("##", "<%>"), variant.encode(data, false, "<%>"))
    }

    @Test
    fun testErrors() {
        try {
            Base64Variant("foobar", "xyz", false, '!', 24)
            fail("Should not pass")
        } catch (e: IllegalArgumentException) {
            verifyException(e, "length must be exactly")
        }

        try {
            Base64Variants.MIME.decode("!@##@%$#%&*^(&)(*")
            fail("Should not pass")
        } catch (e: IllegalArgumentException) {
            verifyException(e, "Illegal character")
        }

        try {
            Base64Variants.MIME.decode("aGVsbG8=!")
            fail("Should not pass")
        } catch (e: IllegalArgumentException) {
            verifyException(e, "Illegal character")
        }
    }

    @Test
    fun testPaddingReadBehaviour() {
        for (variant in BASE64_VARIANTS) {
            try {
                variant.withPaddingForbidden().decode(BASE64_HELLO)
                fail("Should not pass")
            } catch (e: IllegalArgumentException) {
                if (variant !== Base64Variants.MODIFIED_FOR_URL) {
                    verifyException(e, "no padding")
                } else {
                    verifyException(e, "Illegal character")
                }
            }

            try {
                variant.withPaddingRequired().decode(BASE64_HELLO_WITHOUT_PADDING)
                fail("Should not pass")
            } catch (e: IllegalArgumentException) {
                verifyException(e, "expects padding")
            }

            variant.withPaddingAllowed().decode(BASE64_HELLO_WITHOUT_PADDING)
            variant.withPaddingForbidden().decode(BASE64_HELLO_WITHOUT_PADDING)

            if (variant === Base64Variants.MODIFIED_FOR_URL) {
                continue
            }

            variant.withPaddingAllowed().decode(BASE64_HELLO)
            variant.withPaddingRequired().decode(BASE64_HELLO)
        }

        try {
            Base64Variants.MODIFIED_FOR_URL.withPaddingAllowed().decode(BASE64_HELLO)
            fail("Should not pass")
        } catch (e: IllegalArgumentException) {
            verifyException(e, "Illegal character")
        }

        try {
            Base64Variants.MODIFIED_FOR_URL.withPaddingRequired().decode(BASE64_HELLO)
            fail("Should not pass")
        } catch (e: IllegalArgumentException) {
            verifyException(e, "Illegal character")
        }
    }

    companion object {

        private const val BASE64_HELLO = "aGVsbG8="

        private const val BASE64_HELLO_WITHOUT_PADDING = "aGVsbG8"

    }

}