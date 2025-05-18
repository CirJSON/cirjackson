package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

import org.cirjson.cirjackson.annotations.CirJsonCreator
import org.cirjson.cirjackson.annotations.CirJsonValue

enum class CirJsonFormatTypes {

    STRING,

    NUMBER,

    INTEGER,

    BOOLEAN,

    OBJECT,

    ARRAY,

    NULL,

    ANY;

    @CirJsonValue
    fun value(): String {
        return name.lowercase()
    }

    companion object {

        private val ourByLCName = hashMapOf<String, CirJsonFormatTypes>().apply {
            CirJsonFormatTypes.entries.forEach { put(it.name.lowercase(), it) }
        }

        @CirJsonCreator
        fun forValue(string: String): CirJsonFormatTypes? {
            return ourByLCName[string]
        }

    }

}