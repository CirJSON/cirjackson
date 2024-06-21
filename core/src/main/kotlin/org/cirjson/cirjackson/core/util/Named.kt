package org.cirjson.cirjackson.core.util

/**
 * Simple tag interface used primarily to allow databind to pass entities with name without needing to expose more
 * details of implementation.
 */
interface Named {

    val name: String

    class StringAsNamed(override val name: String) : Named {

        override fun toString(): String {
            return name
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }

            if (javaClass != (other as? StringAsNamed)?.javaClass) {
                return false
            }

            return name == other.name
        }

        override fun hashCode(): Int {
            return name.hashCode()
        }
    }

    companion object {


    }

}