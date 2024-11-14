package dev.mattidragon.jsonpatcher.lang.test.sharedTests.expression;

import dev.mattidragon.jsonpatcher.lang.test.SharedTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

public interface ModificationExpressionTests extends SharedTest {
    @Test
    default void testUnaryModification() {
        var code = """
                var a = 0;
                debug.assert(a++ == 0);
                debug.assert(a == 1);
                debug.assert(--a == 0);
                debug.assert(a == 0);
                
                var b = [0];
                debug.assert(b[0]++ == 0);
                debug.assert(b[0] == 1);
                
                var c = {a: 0};
                debug.assert(c.a++ == 0);
                debug.assert(c.a == 2);
                
                #a = 1;
                #var d = [1];
                #delete d[--a];
                #debug.assert(d == []);
                """;
        TestUtils.testCode(runner(), code);
    }
    
    @Test
    default void testAssignment() {
        var code = """
                var a = 0;
                debug.assert((a += 10) == 10, "a failed");
                
                var b = 0;
                debug.assert((b += 10) == 10, "b failed");
                (() -> debug.assert(b == 10, "b failed (lambda)"))();
                
                var c = [0];
                debug.assert((c[0] += 10) == 10, "c failed");
                
                var d = [0];
                debug.assert((d[0] += 10) == 10, "d failed");
                (() -> debug.assert(d[0] == 10, "d failed (lambda)"))();
                
                var e = {a: 0};
                debug.assert((e.a += 10) == 10, "e failed");
                
                var f = {a: 0};
                debug.assert((f.a += 10) == 10, "f failed");
                (() -> debug.assert(f.a == 10, "f failed (lambda)"))();
                """;
        TestUtils.testCode(runner(), code);
    }
}
