package dev.mattidragon.jsonpatcher.lang.test.sharedTests.statement;

import dev.mattidragon.jsonpatcher.lang.test.SharedTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public interface LoopStatementTests extends SharedTest {
    @Test
    default void testWhileBreak() {
        Assertions.assertTimeoutPreemptively(Duration.of(10, ChronoUnit.MILLIS), () -> TestUtils.testCode(runner(), """
                while (true) {
                    break;
                }
                """), "While loop with break took too long to execute");
    }

    @Test
    default void testForEach() {
        
    }
}
