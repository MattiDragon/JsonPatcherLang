package dev.mattidragon.jsonpatcher.lang.parser.test.parser;

import dev.mattidragon.jsonpatcher.lang.parse.metadata.MetadataNull;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MetadataTests {
    @Test
    public void testMetadataWithoutValue() {
        var result = TestUtils.parseFull("@empty;");

        Assertions.assertTrue(result.metadata().has("empty"));
        Assertions.assertInstanceOf(MetadataNull.class, result.metadata().get("empty"));
    }

    @Test
    public void testMetadataWithValue() {
        var result = TestUtils.parseFull("@value 42;");

        Assertions.assertTrue(result.metadata().has("value"));
        Assertions.assertEquals(42, result.metadata().getNumber("value"));
    }
}
