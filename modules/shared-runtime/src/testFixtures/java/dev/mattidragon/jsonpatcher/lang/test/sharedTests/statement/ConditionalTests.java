package dev.mattidragon.jsonpatcher.lang.test.sharedTests.statement;

import dev.mattidragon.jsonpatcher.lang.test.SharedTest;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

public interface ConditionalTests extends SharedTest {
    @Test
    default void testIfElse() {
        var code = """
                function doThing(a, b) {
                    if (a) return "a";
                    else if (b) return "b";
                    else return "c";
                }
                
                debug.assert(doThing(true, false) == "a");
                debug.assert(doThing(false, true) == "b");
                debug.assert(doThing(false, false) == "c");
                """;

        TestUtils.testCode(runner(), code);
    }
    
    @Test
    default void testTernary() {
        var code = """
                val getVal = (a, b) -> a ? b ? 1 : 2 : b ? 3 : 4;
                debug.assert(getVal(true, true) == 1);
                debug.assert(getVal(true, false) == 2);
                debug.assert(getVal(false, true) == 3);
                debug.assert(getVal(false, false) == 4);
                """;

        TestUtils.testCode(runner(), code);
    }
}
