package dev.mattidragon.jsonpatcher.lang.test.sharedTests;

import dev.mattidragon.jsonpatcher.lang.test.SharedTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

public interface RootTests extends SharedTest {
    @Test
    default void testRootShadowing() {
        var code = """
                $value = 0;
                val a = {value: 10};
                val f = () -> $value;
                
                var g = null;
                apply (a) {
                    debug.assert($value == 10);
                    debug.assert(f() == 0);
                    g = () -> $value;
                    debug.assert(g() == 10);
                }
                
                debug.assert(g() == 10);
                """;
        TestUtils.testCode(runner(), code);
    }
}
