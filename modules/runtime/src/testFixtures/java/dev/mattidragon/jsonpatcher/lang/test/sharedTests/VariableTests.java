package dev.mattidragon.jsonpatcher.lang.test.sharedTests;

import dev.mattidragon.jsonpatcher.lang.test.SharedTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public interface VariableTests extends SharedTest {
    @Test
    default void testVariableReassignment() {
        Assertions.assertDoesNotThrow(() -> TestUtils.runCode(runner(), """
                var a = 10;
                a = 20;
                """));
    }
    
    @Test
    default void testIllegalVariableReassignment() {
        Assertions.assertThrows(variableFailException(), () -> TestUtils.runCode(runner(), """
                val a = 10;
                a = 20;
                """));
    }
    
    @Test
    default void testVariableShadowing() {
        Assertions.assertDoesNotThrow(() -> TestUtils.runCode(runner(), """
                val a = 10;
                function f(a) {
                }
                """));
    }
    
    @Test
    default void testIllegalVariableShadowing() {
        Assertions.assertThrows(variableFailException(), () -> TestUtils.runCode(runner(), """
                val a = 10;
                var a = 20;
                """));
    }
    
    @Test
    default void testMissingVariable() {
        Assertions.assertThrows(variableFailException(), () -> TestUtils.runCode(runner(), """
                missingVariable;
                """));
        Assertions.assertThrows(variableFailException(), () -> TestUtils.runCode(runner(), """
                (() -> missingVariable)();
                """));
    }

    @Test
    default void testFibCompile() {
        var code = """
                function fib(index) {
                    if (index == 0 || index == 1) return 1;
                    return fib(index - 2) + fib(index - 1);
                }
                """;
        TestUtils.testCode(runner(), code);
    }

    @Test
    default void testFunctionArgCapture() {
        var code = """
                val f = (a, b) -> {
                    if (b) a = 10;
                    return () -> a;
                };
                debug.assertEquals(f(0, true)(), 10);
                debug.assertEquals(f(0, false)(), 0);
                """;
        TestUtils.testCode(runner(), code);
    }

    @Test
    default void testValidSelfCapture() {
        var code = """
                val f = () -> f;
                debug.assertEquals(f(), f);
                """;
        TestUtils.testCode(runner(), code);
    }

    @Test
    default void testInvalidSelfCapture() {
        var code = """
                val f = (() -> f)();
                """;
        Assertions.assertThrows(NullPointerException.class, () -> TestUtils.runCode(runner(), code));
    }
}
