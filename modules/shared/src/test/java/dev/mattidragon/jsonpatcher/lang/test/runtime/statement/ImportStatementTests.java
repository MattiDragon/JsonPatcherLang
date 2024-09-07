package dev.mattidragon.jsonpatcher.lang.test.runtime.statement;

import dev.mattidragon.jsonpatcher.lang.runtime.Runtime;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.legacy.EvaluationException;
import dev.mattidragon.jsonpatcher.lang.test.RuntimeTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;

public class ImportStatementTests {
    @RuntimeTest
    public void testSimpleImport(Runtime runtime) {
        var result = TestUtils.parseFull("""
                import "lib_name" as varName;
                import "lib_name" as varName2;
                debug.assert(varName.a == 1 && varName2.a == 1);
                """);
        Assertions.assertDoesNotThrow(() -> runtime.prepare(result.program(), result.treeMetadata(), TestUtils.PREPARE_CONTEXT_BUILDER_CONSUMER).run(
                builder -> builder
                        .debugConsumer(TestUtils.EMPTY_DEBUG_CONSUMER)
                        .addStdlib()
                        .libraryLocator((libraryName, libraryObject, context) -> {
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
        Assertions.assertThrowsExactly(EvaluationException.class, () -> runtime.prepare(result.program(), result.treeMetadata(), TestUtils.PREPARE_CONTEXT_BUILDER_CONSUMER).run(
                builder -> builder
                        .debugConsumer(TestUtils.EMPTY_DEBUG_CONSUMER)
                        .addStdlib()
                        .libraryLocator((libraryName, libraryObject, context) -> {
                            // test library locator always returns a library
                            libraryObject.value().put("a", new Value.NumberValue(1));
                        }),
                TestUtils.CONFIG));
    }
}
