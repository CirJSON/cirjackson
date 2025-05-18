package org.cirjson.cirjackson.databind.cirjsonFormatVisitors

import org.cirjson.cirjackson.databind.SerializerProvider

interface CirJsonFormatVisitorWithSerializerProvider {

    var provider: SerializerProvider?

}