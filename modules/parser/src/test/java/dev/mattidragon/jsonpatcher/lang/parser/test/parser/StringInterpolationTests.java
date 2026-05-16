package dev.mattidragon.jsonpatcher.lang.parser.test.parser;

import dev.mattidragon.jsonpatcher.lang.ast.expression.StringInterpolationExpression;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class StringInterpolationTests {
    @Test
    public void testNestedTernaryParsing() {
        var e = Assertions.assertDoesNotThrow(() -> TestUtils.parseExpression("""
                "a\\{1}b\\{2}c\\{3}"
                """), "test should parse");
        Assertions.assertEquals(
                new StringInterpolationExpression(
                        List.of("a", "b", "c", ""),
                        List.of(
                                TestUtils.numberExpression(1),
                                TestUtils.numberExpression(2),
                                TestUtils.numberExpression(3)
                        )
                ),
                e,
                "expression should match"
        );
    }
}
