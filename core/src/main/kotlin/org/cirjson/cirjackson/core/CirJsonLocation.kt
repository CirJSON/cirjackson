package org.cirjson.cirjackson.core

/**
 * Object that encapsulates Location information used for reporting parsing (or potentially generation) errors, as well
 * as current location within input streams.
 *
 * NOTE: users should be careful if using [equals] implementation as it may or may not compare underlying "content
 * reference" for equality. Instead, it would make sense to explicitly implementing equality checks using specific
 * criteria caller desires.
 */
class CirJsonLocation {

    companion object {

        val NA = CirJsonLocation()

    }

}