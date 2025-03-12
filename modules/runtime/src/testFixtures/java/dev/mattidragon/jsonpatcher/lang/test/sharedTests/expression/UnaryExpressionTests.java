package dev.mattidragon.jsonpatcher.lang.test.sharedTests.expression;

import dev.mattidragon.jsonpatcher.lang.test.SharedTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

public interface UnaryExpressionTests extends SharedTest {
    @Test
    default void testNot() {
        var code = """
                debug.assert((!true) == false);
                debug.assert((!false) == true);
                """;
        TestUtils.testCode(runner(), code);
    }

    @Test
    default void testMinus() {
        var code = """
                debug.assert(-10 == 10 * -1);
                debug.assert(- -10 == 10);
                debug.assert(-0.5 != -1);
                debug.assert(-0.5 != -0);
                """;
        TestUtils.testCode(runner(), code);
    }

    @Test
    default void testBitwiseNot() {
        var code = """
                debug.assert(~0 == -1);
                debug.assert(~100 is number);
                """;
        TestUtils.testCode(runner(), code);
    }
}
