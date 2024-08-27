package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.TestBase
import kotlin.test.Test
import kotlin.test.assertTrue

class RawValueWithSurrogatesTest : TestBase() {

    @Test
    fun testRawWithSurrogates() {
        for (mode in ALL_GENERATOR_MODES) {
            for (useCharArray in booleanArrayOf(true, false)) {
                rawWithSurrogates(mode, useCharArray)
            }
        }
    }

    private fun rawWithSurrogates(mode: Int, useCharArray: Boolean) {
        for (i in OFFSET..<COUNT) {
            val stringBuilder = StringBuilder(1000)

            for (j in 0..<i) {
                stringBuilder.append(' ')
            }

            stringBuilder.append(SURROGATES)
            val text = stringBuilder.toString()

            val generator = createGenerator(mode)

            if (useCharArray) {
                val ch = text.toCharArray()
                generator.writeRawValue(ch, OFFSET, ch.size - OFFSET)
            } else {
                generator.writeRawValue(text, OFFSET, text.length - OFFSET)
            }

            generator.close()
            assertTrue(generator.streamWriteOutputTarget!!.toString().isNotEmpty())
        }
    }

    companion object {

        private const val OFFSET = 3

        private const val COUNT = 100

        private const val SURROGATES = "{\n" +
                "  \"__cirJsonId__\": \"0\",\n" +
                "  \"xxxxxxx\": {\n" +
                "    \"__cirJsonId__\": \"1\",\n" +
                "    \"xxxx\": \"xxxxxxxxx\",\n" +
                "    \"xx\": \"xxxxxxxxxxxxxxxxxxx\",\n" +
                "    \"xxxxxxxxx\": \"xxxx://xxxxxxx.xxx\",\n" +
                "    \"xxxxxx\": {\n" +
                "      \"__cirJsonId__\": \"2\",\n" +
                "      \"xxxx\": \"xxxxxxxxxxx\",\n" +
                "      \"xxxxxxxx\": {\n" +
                "        \"__cirJsonId__\": \"3\",\n" +
                "        \"xxxxxxxxxxx\": \"xx\",\n" +
                "        \"xxxxxxxxxx\": \"xx-xx\"\n" +
                "      }\n" +
                "    },\n" +
                "    \"xxxxx\": [\n" +
                "      \"4\",\n" +
                "      {\n" +
                "        \"__cirJsonId__\": \"5\",\n" +
                "        \"xxxx\": \"xxxx\",\n" +
                "        \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\"\n" +
                "      }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"xxxxxxxxxxx\": [\n" +
                "    \"6\",\n" +
                "    {\n" +
                "      \"__cirJsonId__\": \"7\",\n" +
                "      \"xxxxxxx\": \"xxxxxx\",\n" +
                "      \"xxxxxxxx\": [\n" +
                "        \"8\",\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"9\",\n" +
                "          \"xxxxxx\": x,\n" +
                "          \"xxxxxx\": xxx,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"10\",\n" +
                "            \"xxxxxx\": xx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"11\",\n" +
                "          \"xxxxxx\": x,\n" +
                "          \"xxxxxx\": xxx,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"12\",\n" +
                "            \"xxxxxx\": xxx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"__cirJsonId__\": \"13\",\n" +
                "      \"xxxxxxxxxxx\": \"xxxxxx\",\n" +
                "      \"xxxxxxxx\": [\n" +
                "        \"14\",\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"15\",\n" +
                "          \"xxxxxx\": x,\n" +
                "          \"xxxxxx\": xxx,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"16\",\n" +
                "            \"xxxxxx\": xx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"17\",\n" +
                "          \"xxxxxx\": x,\n" +
                "          \"xxxxxx\": xxx,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"18\",\n" +
                "            \"xxxxxx\": xxx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"__cirJsonId__\": \"19\",\n" +
                "      \"xxxxxxxxxxx\": \"xxxxxx\",\n" +
                "      \"xxxxxxxx\": [\n" +
                "        \"20\",\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"21\",\n" +
                "          \"xxxxxx\": x,\n" +
                "          \"xxxxxx\": xxx,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"22\",\n" +
                "            \"xxxxxx\": xx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"23\",\n" +
                "          \"xxxxxx\": x,\n" +
                "          \"xxxxxx\": xxx,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"24\",\n" +
                "            \"xxxxxx\": xxx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"__cirJsonId__\": \"25\",\n" +
                "      \"xxxxxxxxxxx\": \"xxxxxx\",\n" +
                "      \"xxxxxxxx\": [\n" +
                "        \"26\",\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"27\",\n" +
                "          \"xxxxxx\": x,\n" +
                "          \"xxxxxx\": xxx,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"28\",\n" +
                "            \"xxxxxx\": xx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"29\",\n" +
                "          \"xxxxxx\": x,\n" +
                "          \"xxxxxx\": xxx,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"30\",\n" +
                "            \"xxxxxx\": xxx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"__cirJsonId__\": \"31\",\n" +
                "      \"xxxxxxxxxxx\": \"xxxxxx\",\n" +
                "      \"xxxxxxxx\": [\n" +
                "        \"32\",\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"33\",\n" +
                "          \"xxxxxx\": 3,\n" +
                "          \"xxxxxx\": 123,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"34\",\n" +
                "            \"xxxxxx\": 24,\n" +
                "            \"xxxxxx\": 4,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"35\",\n" +
                "          \"xxxxxx\": 0,\n" +
                "          \"xxxxxx\": 123,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"36\",\n" +
                "            \"xxxxxx\": 123,\n" +
                "            \"xxxxxx\": 1,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"__cirJsonId__\": \"37\",\n" +
                "      \"xxxxxxxxxxx\": \"xxxxxx\",\n" +
                "      \"xxxxxxxx\": [\n" +
                "        \"38\",\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"39\",\n" +
                "          \"xxxxxx\": 1,\n" +
                "          \"xxxxxx\": 123,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"40\",\n" +
                "            \"xxxxxx\": xx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"41\",\n" +
                "          \"xxxxxx\": x,\n" +
                "          \"xxxxxx\": 123,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"42\",\n" +
                "            \"xxxxxx\": xxx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    },\n" +
                "    {\n" +
                "      \"__cirJsonId__\": \"43\",\n" +
                "      \"xxxxxxxxxxx\": \"xxxxx\",\n" +
                "      \"xxxxxxxx\": [\n" +
                "        \"44\",\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"45\",\n" +
                "          \"xxxxxx\": x,\n" +
                "          \"xxxxxx\": xxx,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"46\",\n" +
                "            \"xxxxxx\": xx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"__cirJsonId__\": \"47\",\n" +
                "          \"xxxxxx\": x,\n" +
                "          \"xxxxxx\": xxx,\n" +
                "          \"xxxx\": \"xx xxxxxxxxxxx: xxxxxxx xxxxxx xxxxxxxxxxxxx xxxxx xxxxxx. xxxxx xxxxxx xxxxx xxxxx. xx xxx xx xxxx xxx xxxx. xxxx xxxxx xxx xxxxxxxx xxxxx xxxxxx xxxxxxx\uD83D\uDE06\uD83D\uDC4D\",\n" +
                "          \"xxxxxx\": {\n" +
                "            \"__cirJsonId__\": \"48\",\n" +
                "            \"xxxxxx\": xxx,\n" +
                "            \"xxxxxx\": x,\n" +
                "            \"xxxx\": \"xxxxx\"\n" +
                "          }\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}"

    }

}