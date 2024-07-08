package org.cirjson.cirjackson.core.cirjson

import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.core.CirJsonLocation
import org.cirjson.cirjackson.core.CirJsonParser

/**
 * Helper class used if [org.cirjson.cirjackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION] is enabled. Optimized
 * to try to limit memory usage and processing overhead for smallest entries, but without adding trashing (immutable
 * objects would achieve optimal memory usage but lead to significant number of discarded temp objects for scopes with
 * large number of entries). Another consideration is trying to limit actual number of compiled classes as it
 * contributes significantly to overall jar size (due to linkage etc.).
 *
 * @property source Source object (parser/generator) used to construct this detector.
 */
class DuplicateDetector private constructor(val source: Any) {

    private var mySeen: HashSet<String>? = null

    private var myFirstName: String? = null

    private var mySecondName: String? = null

    fun child(): DuplicateDetector {
        return DuplicateDetector(source)
    }

    fun findLocation(): CirJsonLocation? {
        return (source as? CirJsonParser)?.currentLocation()
    }

    fun reset() {
        mySeen = null
        myFirstName = null
        mySecondName = null
    }

    /**
     * Method called to check whether a newly encountered property name would be a duplicate within this context, and if
     * not, update the state to remember having seen the property name for checking more property names.
     *
     * @param name Property seen
     *
     * @return `true` if the property had already been seen before in this context
     */
    fun isDuplicate(name: String): Boolean {
        return when {
            myFirstName == null -> {
                myFirstName = name
                false
            }

            name == myFirstName -> true

            mySecondName == null -> {
                mySecondName = name
                false
            }

            name == mySecondName -> true

            else -> {
                if (mySeen == null) {
                    mySeen = HashSet(16)
                    mySeen!!.add(myFirstName!!)
                    mySeen!!.add(mySecondName!!)
                }

                mySeen!!.add(name)
            }
        }
    }

    companion object {

        fun rootDetector(parser: CirJsonParser): DuplicateDetector {
            return DuplicateDetector(parser)
        }

        fun rootDetector(generator: CirJsonGenerator): DuplicateDetector {
            return DuplicateDetector(generator)
        }

    }

}