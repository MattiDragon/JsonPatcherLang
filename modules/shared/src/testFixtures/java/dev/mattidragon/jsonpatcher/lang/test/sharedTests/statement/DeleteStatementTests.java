package dev.mattidragon.jsonpatcher.lang.test.sharedTests.statement;

import dev.mattidragon.jsonpatcher.lang.test.SharedTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

public interface DeleteStatementTests extends SharedTest {
    @Test
    default void testPropertyDelete() {
        TestUtils.testCode(runner(), """
                val obj = {a: 1, b: 2};
                debug.assert("a" in obj);
                delete obj.a;
                debug.assert(!("a" in obj));
                """);
    }

    @Test
    default void testArrayDelete() {
        TestUtils.testCode(runner(), """
                val arr = [1, 2];
                debug.assert(arr.length == 2);
                delete arr[0];
                debug.assert(arr == [2]);
                """);
    }
}
