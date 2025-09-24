package dev.mattidragon.jsonpatcher.lang.test.sharedTests.stdlib;

import dev.mattidragon.jsonpatcher.lang.runtime.PatchException;
import dev.mattidragon.jsonpatcher.lang.test.SharedTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public interface DebugLibTests extends SharedTest {
    @Test
    default void testAssertPass() {
        TestUtils.testCode(runner(), """
                debug.assert(true, "This should not fail");
                """);
    }

    @Test
    default void testAssertFail() {
        Assertions.assertThrows(PatchException.class, () -> {
            TestUtils.runCode(runner(), """
                    debug.assert(false, "This should fail");
                    """);
        });
    }

    @Test
    default void testThrow() {
        Assertions.assertThrows(PatchException.class, () -> {
            TestUtils.runCode(runner(), """
                    debug.throw("This should throw");
                    """);
        });
    }

    @Test
    default void testAssertEqualsPass() {
        TestUtils.testCode(runner(), """
                debug.assertEquals(10, 10, "This should not fail");
                debug.assertEquals("hello", "hello", "This should not fail");
                debug.assertEquals([1, 2, 3], [1, 2, 3], "This should not fail");
                debug.assertEquals({ a: 1, b: 2 }, { a: 1, b: 2 }, "This should not fail");
                """);
    }

    @Test
    default void testAssertEqualsFail() {
        Assertions.assertThrows(PatchException.class, () -> {
            TestUtils.runCode(runner(), """
                    debug.assertEquals(10, 20, "This should fail");
                    """);
        });
    }
}
