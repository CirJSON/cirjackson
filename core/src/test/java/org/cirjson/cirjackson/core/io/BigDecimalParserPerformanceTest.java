package org.cirjson.cirjackson.core.io;

import org.cirjson.cirjackson.core.CirJsonParser;
import org.cirjson.cirjackson.core.CirJsonToken;
import org.cirjson.cirjackson.core.ObjectReadContext;
import org.cirjson.cirjackson.core.TestBase;
import org.cirjson.cirjackson.core.cirjson.CirJsonFactory;
import org.cirjson.cirjackson.core.exception.StreamConstraintsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class BigDecimalParserPerformanceTest extends TestBase {

    private final CirJsonFactory myFactory = new CirJsonFactory();

    @Test
    @Timeout(value = 3000, unit = TimeUnit.MILLISECONDS)
    @SuppressWarnings("StringRepeatCanBeUsed")
    public void testBigDecimalFromString() {
        StringBuilder stringBuilder = new StringBuilder(900);

        for (int i = 0; i < 500; ++i) {
            stringBuilder.append('1');
        }

        stringBuilder.append("1e10000000");
        String doc = stringBuilder.toString();

        try (CirJsonParser parser = myFactory.createParser(ObjectReadContext.Companion.empty(), doc)) {
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());
            assertNotNull(parser.getBigDecimalValue());
        }
    }

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testBigIntegerViaBigDecimal() {
        String doc = "1e25000000";

        try (CirJsonParser parser = myFactory.createParser(ObjectReadContext.Companion.empty(), doc)) {
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());

            try {
                parser.getBigIntegerValue();
                fail("Should not pass");
            } catch (StreamConstraintsException e) {
                verifyException(e, "BigDecimal scale (-25000000) magnitude exceeds the maximum allowed (100000)");
            }
        }
    }

    @Test
    @Timeout(value = 2000, unit = TimeUnit.MILLISECONDS)
    public void testTinyIntegerViaBigDecimal() {
        String doc = "1e25000000";

        try (CirJsonParser parser = myFactory.createParser(ObjectReadContext.Companion.empty(), doc)) {
            assertToken(CirJsonToken.VALUE_NUMBER_FLOAT, parser.nextToken());

            try {
                parser.getBigIntegerValue();
                fail("Should not pass");
            } catch (StreamConstraintsException e) {
                verifyException(e, "BigDecimal scale (-25000000) magnitude exceeds the maximum allowed (100000)");
            }
        }
    }

}
