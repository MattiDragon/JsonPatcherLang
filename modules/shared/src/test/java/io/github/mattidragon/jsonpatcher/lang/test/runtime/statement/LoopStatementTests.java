package io.github.mattidragon.jsonpatcher.lang.test.runtime.statement;

import dev.mattidragon.jsonpatcher.lang.test.RuntimeTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import io.github.mattidragon.jsonpatcher.lang.runtime.Runtime;
import org.junit.jupiter.api.Assertions;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class LoopStatementTests {
    @RuntimeTest
    public void testWhileBreak(Runtime runtime) {
        Assertions.assertTimeoutPreemptively(Duration.of(10, ChronoUnit.MILLIS), () -> TestUtils.testCode(runtime, """
                while (true) {
                    break;
                }
                """), "While loop with break took too long to execute");
    }
}
