package org.cirjson.cirjackson.core.support

import org.cirjson.cirjackson.core.CirJsonParser
import org.cirjson.cirjackson.core.CirJsonToken
import org.cirjson.cirjackson.core.async.ByteArrayFeeder
import org.cirjson.cirjackson.core.exception.StreamReadException
import kotlin.math.min

class AsyncByteArrayReaderWrapper(parser: CirJsonParser, private val myBytesPerFeed: Int, private val myDoc: ByteArray,
        private val myPadding: Int) : AsyncReaderWrapperBase(parser) {

    private var myOffset = 0

    private val myEnd = myDoc.size

    override fun nextToken(): CirJsonToken? {
        var token: CirJsonToken?

        while (parser.nextToken().also { token = it } == CirJsonToken.NOT_AVAILABLE) {
            val feeder = parser.nonBlockingInputFeeder() as ByteArrayFeeder

            if (!feeder.isNeedingMoreInput) {
                throw StreamReadException(null, "Got NOT_AVAILABLE, could not feed more input")
            }

            val amount = min(myBytesPerFeed, myEnd - myOffset)

            if (amount < 1) {
                feeder.endOfInput()
                continue
            }

            if (myPadding == 0) {
                feeder.feedInput(myDoc, myOffset, myOffset + amount)
            } else {
                val temp = ByteArray(amount + myPadding + myPadding)
                myDoc.copyInto(temp, myPadding, myOffset, myOffset + amount)
                feeder.feedInput(temp, myPadding, myPadding + amount)
            }

            myOffset += amount
        }

        return token
    }

}