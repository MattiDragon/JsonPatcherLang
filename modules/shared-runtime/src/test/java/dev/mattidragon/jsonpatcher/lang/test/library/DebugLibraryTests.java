package dev.mattidragon.jsonpatcher.lang.test.library;

import dev.mattidragon.jsonpatcher.lang.runtime_shared.Value;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.stdlib.Libraries;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DebugLibraryTests {
    private static final Libraries.DebugLibrary LIBRARY = new Libraries.DebugLibrary();

    @Test
    public void testThrow() {
        var context = TestUtils.createTestFunctionContext();
        Assertions.assertThrows(RuntimeException.class, () -> LIBRARY.throw_(context, new Value.StringValue("Test error")), "Throwing an exception should throw");
    }

    @Test
    public void testAssert() {
        var context = TestUtils.createTestFunctionContext();
        Assertions.assertThrows(RuntimeException.class, () -> LIBRARY.assert_(context, Value.BooleanValue.FALSE), "Asserting false should throw");
        Assertions.assertThrows(RuntimeException.class, () -> LIBRARY.assert_(context, Value.NullValue.NULL), "Asserting null should throw");
    }
}
