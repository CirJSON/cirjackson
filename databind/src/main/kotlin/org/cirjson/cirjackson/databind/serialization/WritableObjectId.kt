package org.cirjson.cirjackson.databind.serialization

import org.cirjson.cirjackson.annotations.ObjectIdGenerator
import org.cirjson.cirjackson.core.CirJacksonException
import org.cirjson.cirjackson.core.CirJsonGenerator
import org.cirjson.cirjackson.databind.serialization.implementation.ObjectIdWriter

/**
 * Simple value container used to keep track of Object Ids during serialization.
 */
class WritableObjectId(val generator: ObjectIdGenerator<*>) {

    var id: Any? = null

    /**
     * Marker to denote whether Object ID value has been written as part of an Object, to be referencable. Remains
     * `false` when forward-reference is written.
     */
    private var myIdWritten = false

    /**
     * Method to call to write a reference to object that this id refers. Usually this is done after an earlier call to
     * [writeAsDeclaration].
     */
    @Throws(CirJacksonException::class)
    fun writeAsReference(generator: CirJsonGenerator, writer: ObjectIdWriter): Boolean {
        if (id == null || !myIdWritten && !writer.alwaysAsId) {
            return false
        }

        generator.writeObjectId(id.toString())
        return true
    }

    fun generateId(forPojo: Any?): Any? {
        return id ?: generator.generateId(forPojo).also { id = it }
    }

    /**
     * Method called to output Object ID declaration using native Object ID write method
     * [CirJsonGenerator.writeObjectId].
     */
    @Throws(CirJacksonException::class)
    fun writeAsDeclaration(generator: CirJsonGenerator) {
        myIdWritten = true
        val idString = id!!.toString()
        generator.writeObjectId(idString)
    }

}