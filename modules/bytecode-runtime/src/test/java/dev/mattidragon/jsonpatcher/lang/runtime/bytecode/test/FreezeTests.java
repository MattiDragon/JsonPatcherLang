package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.test;

import dev.mattidragon.jsonpatcher.lang.runtime.bytecode.compiler.CompilerOptions;
import dev.mattidragon.jsonpatcher.lang.test.TestRunner;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FreezeTests {
    private final TestRunner runner = new BytecodeTestRunner(CompilerOptions.DEFAULT);

    @Test
    public void testFreeze() {
        TestUtils.testCode(runner, """
                val a = {c: 1};
                val b = values.freeze(a);
                a.c = 2;
                debug.assert(b.c == 1);
                """);

        TestUtils.testCode(runner, """
                val a = [1];
                val b = values.freeze(a);
                a[0] = 2;
                debug.assert(b[0] == 1);
                """);
    }

    @Test
    public void testFrozenThrows() {
        Assertions.assertThrows(RuntimeException.class, () -> {
            TestUtils.runCode(runner, """
                    values.freeze({a: "aa"}).a = "bb";
                    """);
        });

        Assertions.assertThrows(RuntimeException.class, () -> {
            TestUtils.runCode(runner, """
                    values.freeze(["aa"])[0] = "bb";
                    """);
        });
    }
}
