package org.cirjson.cirjackson.core.type;

import org.cirjson.cirjackson.core.TestBase;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class TypeReferenceTest extends TestBase {

    @Test
    public void testSimple() {
        TypeReference<?> reference = new TypeReference<List<String>>() {};
        assertNotNull(reference);
        assertNotNull(reference.getType());
    }

    @Test
    @SuppressWarnings("rawtypes")
    public void testInvalid() {
        assertThrows(IllegalArgumentException.class, () -> {
            TypeReference<?> reference = new TypeReference() {};
            fail("Should not pass, got: " + reference);
        });
    }

}
