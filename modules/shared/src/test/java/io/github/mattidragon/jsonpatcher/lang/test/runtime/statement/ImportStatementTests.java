package io.github.mattidragon.jsonpatcher.lang.test.runtime.statement;

import dev.mattidragon.jsonpatcher.lang.test.RuntimeTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationException;
import io.github.mattidragon.jsonpatcher.lang.runtime.Runtime;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import org.junit.jupiter.api.Assertions;

public class ImportStatementTests {
    @RuntimeTest
    public void testSimpleImport(Runtime runtime) {
        var result = TestUtils.parseFull("""
                import "lib_name" as varName;
                import "lib_name" as varName2;
                debug.assert(varName.a == 1 && varName2.a == 1);
                """);
        Assertions.assertDoesNotThrow(() -> runtime.prepare(result.program(), result.treeMetadata()).run(
                builder -> builder
                        .debugConsumer(TestUtils.EMPTY_DEBUG_CONSUMER)
                        .libraryLocator((libraryName, libraryObject, importPos, config) -> {
                            // test library locator always returns a library
                            libraryObject.value().put("a", new Value.NumberValue(1));
                        }),
                TestUtils.CONFIG));
    }
    
    @RuntimeTest
    public void testImportFail(Runtime runtime) {
        var result = TestUtils.parseFull("""
                import "lib_name" as varName;
                import "lib_name" as varName;
                """);
        Assertions.assertThrowsExactly(EvaluationException.class, () -> runtime.prepare(result.program(), result.treeMetadata()).run(
                builder -> builder
                        .debugConsumer(TestUtils.EMPTY_DEBUG_CONSUMER)
                        .libraryLocator((libraryName, libraryObject, importPos, config) -> {
                            // test library locator always returns a library
                            libraryObject.value().put("a", new Value.NumberValue(1));
                        }),
                TestUtils.CONFIG));
    }
}
