package org.cirjson.cirjackson.core

import org.cirjson.cirjackson.core.util.CirJacksonFeature

/**
 * Marker interface that is to be implemented by data format - specific features. Interface used since Enums can not
 * extend classes or other Enums, but they can implement interfaces; and as such we may be able to use limited amount of
 * generic functionality.
 *
 * At this point this type is more of an extra marker feature, as its core API is now defined in more general
 * [CirJacksonFeature].
 */
interface FormatFeature : CirJacksonFeature
