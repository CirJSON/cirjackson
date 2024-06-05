package org.cirjson.cirjackson.core.util

/**
 * Simple tag interface used primarily to allow databind to pass entities with name without needing to expose more
 * details of implementation.
 */
interface Named {

    val name: String

}