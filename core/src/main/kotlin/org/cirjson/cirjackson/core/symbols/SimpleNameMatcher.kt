package org.cirjson.cirjackson.core.symbols

import org.cirjson.cirjackson.core.util.Named
import java.util.*

/**
 * Basic [PropertyNameMatcher] that uses case-sensitive match and does not require (or expect) that names passed as
 * arguments have been [String.intern]ed.
 */
class SimpleNameMatcher : HashedMatcherBase {

    private constructor(locale: Locale?, names: Array<String?>, offsets: IntArray, mask: Int) : super(locale, null,
            null, names, offsets, mask)

    private constructor(primary: SimpleNameMatcher, secondary: SimpleNameMatcher) : super(primary, secondary)

    companion object {

        /**
         * Factory method for constructing case-sensitive matcher that only supports matching from `String`.
         *
         * @param locale Locale to use (relevant for case-insensitive matchers)
         * @param propertyNames Names to match
         * @param alreadyInterned Whether underlying Strings have been `String.intern()`ed or not
         *
         * @return Matcher constructed
         */
        fun constructFrom(locale: Locale?, propertyNames: List<Named?>, alreadyInterned: Boolean): SimpleNameMatcher {
            return construct(locale, stringsFromNames(propertyNames, alreadyInterned))
        }

        fun construct(locale: Locale?, propertyNames: List<String?>): SimpleNameMatcher {
            val nameCount = propertyNames.size
            val hashSize = findSize(nameCount)
            val allocSize = hashSize + (hashSize shr 1)

            var names = arrayOfNulls<String>(allocSize)
            var offsets = IntArray(allocSize) { MATCH_UNKNOWN_NAME }

            val mask = hashSize - 1
            var spillPointer = allocSize

            for (i in propertyNames.indices) {
                val name = propertyNames[i] ?: continue
                var ix = hash(name.hashCode(), mask)

                if (names[ix] == null) {
                    names[ix] = name
                    offsets[ix] = i
                    continue
                }

                ix = (mask + 1) + (ix shr 1)

                if (names[ix] == null) {
                    names[ix] = name
                    offsets[ix] = i
                    continue
                }

                if (names.size == spillPointer) {
                    val newSize = names.size + 4
                    names = names.copyOf(newSize)
                    offsets = offsets.copyOf(newSize)
                }

                names[spillPointer] = name
                offsets[spillPointer] = i
                spillPointer++
            }

            return SimpleNameMatcher(locale, names, offsets, mask)
        }

        fun constructCaseInsensitive(locale: Locale, propertyNames: List<Named?>,
                alreadyInterned: Boolean): SimpleNameMatcher {
            return constructCaseInsensitive(locale, stringsFromNames(propertyNames, alreadyInterned))
        }

        fun constructCaseInsensitive(locale: Locale, propertyNames: List<String?>): SimpleNameMatcher {
            return SimpleNameMatcher(construct(locale, propertyNames),
                    construct(locale, lowercase(locale, propertyNames)))
        }

    }

}