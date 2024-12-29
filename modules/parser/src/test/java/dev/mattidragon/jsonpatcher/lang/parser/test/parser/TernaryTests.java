package dev.mattidragon.jsonpatcher.lang.parser.test.parser;

import dev.mattidragon.jsonpatcher.lang.ast.expression.TernaryExpression;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TernaryTests {
    @Test
    public void testNestedTernaryParsing() {
        var e = Assertions.assertDoesNotThrow(() -> TestUtils.parseExpression("1 ? 2 ? 3 : 4 : 5 ? 6 : 7"), "test should parse");
        Assertions.assertEquals(
                new TernaryExpression(
                        TestUtils.numberExpression(1),
                        new TernaryExpression(
                                TestUtils.numberExpression(2),
                                TestUtils.numberExpression(3),
                                TestUtils.numberExpression(4)
                        ),
                        new TernaryExpression(
                                TestUtils.numberExpression(5),
                                TestUtils.numberExpression(6),
                                TestUtils.numberExpression(7)
                        )
                ),
                e,
                "expression should match"
        );
    }
}
