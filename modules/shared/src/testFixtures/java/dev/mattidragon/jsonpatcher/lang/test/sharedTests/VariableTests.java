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
}
