package dev.mattidragon.jsonpatcher.lang.test.sharedTests.statement;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.test.SharedTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public interface ImportStatementTests extends SharedTest {
    @Test
    default void testSimpleImport() {
        var code = """
                import "lib_name" as varName;
                import "lib_name" as varName2;
                debug.assert(varName.a == 1 && varName2.a == 1);
                """;
        Assertions.assertDoesNotThrow(() -> TestUtils.runCode(runner(), code, Map.of("lib_name", new Value.ObjectValue(Map.of("a", new Value.NumberValue(1))))));
    }

    @Test
    default void testImportFail() {
        var code = """
                import "lib_name" as varName;
                import "lib_name" as varName;
                """;
        Assertions.assertThrowsExactly(variableFailException(), () -> TestUtils.runCode(runner(), code, Map.of("lib_name", new Value.ObjectValue(Map.of("a", new Value.NumberValue(1))))));
    }
}
