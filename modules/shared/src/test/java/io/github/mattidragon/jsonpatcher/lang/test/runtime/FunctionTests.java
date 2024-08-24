package io.github.mattidragon.jsonpatcher.lang.test.runtime;

import dev.mattidragon.jsonpatcher.lang.test.RuntimeTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import io.github.mattidragon.jsonpatcher.lang.runtime.Runtime;

public class FunctionTests {
    @RuntimeTest
    public void complexArgPassing(Runtime runtime) {
        var code = """
                function test(a, $ = {a: 3}, b = 1, c*) {
                    debug.assert(a == 3, "first");
                    debug.assert($a == 4, "second");
                    debug.assert(b == 10, "third");
                    debug.assert(c == [1, 2, 3], "fourth");
                }
                test(3, {a: 4}, 10, 1, 2, 3);
                """;
        TestUtils.testCode(runtime, code);
    }
    
    @RuntimeTest
    public void defaultValues(Runtime runtime) {
        var code = """
                function test($ = {a: 3}, b = 1, c*) {
                    debug.assert($a == 3, "first");
                    debug.assert(b == 1, "second");
                    debug.assert(c == [], "third");
                }
                test();
                """;
        TestUtils.testCode(runtime, code);
    }
    
    @RuntimeTest
    public void simpleFunction(Runtime runtime) {
        var code = """
                function test(a, b, c) {
                    debug.assert(a == 1, "first");
                    debug.assert(b == 2, "second");
                    debug.assert(c == 3, "third");
                }
                test(1, 2, 3);
                """;
        TestUtils.testCode(runtime, code);
    }
}
