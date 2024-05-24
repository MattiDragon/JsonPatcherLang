package io.github.mattidragon.jsonpatcher.lang.test.runtime;

import io.github.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

public class FunctionTests {
    @Test
    public void complexArgPassing() {
        var code = """
                function test(a, $ = {a: 3}, b = 1, c*) {
                    debug.assert(a == 3, "first");
                    debug.assert($a == 4, "second");
                    debug.assert(b == 10, "third");
                    debug.assert(c == [1, 2, 3], "fourth");
                }
                test(3, {a: 4}, 10, 1, 2, 3);
                """;
        TestUtils.testCode(code);
    }
    
    @Test
    public void defaultValues() {
        var code = """
                function test($ = {a: 3}, b = 1, c*) {
                    debug.assert($a == 3, "first");
                    debug.assert(b == 1, "second");
                    debug.assert(c == [], "third");
                }
                test();
                """;
        TestUtils.testCode(code);
    }
    
    @Test
    public void simpleFunction() {
        var code = """
                function test(a, b, c) {
                    debug.assert(a == 1, "first");
                    debug.assert(b == 2, "second");
                    debug.assert(c == 3, "third");
                }
                test(1, 2, 3);
                """;
        TestUtils.testCode(code);
    }
}
