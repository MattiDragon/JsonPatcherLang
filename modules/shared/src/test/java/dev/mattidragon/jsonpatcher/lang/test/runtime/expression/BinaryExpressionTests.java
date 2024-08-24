package dev.mattidragon.jsonpatcher.lang.test.runtime.expression;

import dev.mattidragon.jsonpatcher.lang.test.RuntimeTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import dev.mattidragon.jsonpatcher.lang.runtime.Runtime;

public class BinaryExpressionTests {
    // TODO: implement more tests. Not very high priority as these are unlikely to be broken
    // There's no real reason to test the actual expression as it's trivial. Instead, we test the operator implementations.
    @RuntimeTest
    public void testPlus(Runtime runtime) {
        TestUtils.testCode(runtime, """
                val assert = debug.assert;
                assert(1 + 2 == 3);
                assert("1" + "2" == "12");
                assert([1] + [2] == [1, 2]);
                assert({a: 1} + {b: 2} == {a: 1, b: 2});
                """);
        // TODO: implement tests for error cases
    }
}
