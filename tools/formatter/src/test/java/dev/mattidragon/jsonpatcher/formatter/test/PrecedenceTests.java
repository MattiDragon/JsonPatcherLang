package dev.mattidragon.jsonpatcher.formatter.test;

import dev.mattidragon.jsonpatcher.lang.ast.expression.BinaryExpression;
import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import org.junit.jupiter.api.Test;

public class PrecedenceTests {
    @Test
    public void testBasicPrecedence() {
        FormatValidator.validate(new BinaryExpression(
                TestUtils.numberExpression(1),
                new BinaryExpression(
                        TestUtils.numberExpression(2),
                        TestUtils.numberExpression(3),
                        BinaryExpression.Operator.MULTIPLY
                ),
                BinaryExpression.Operator.PLUS
        ));
        FormatValidator.validate(new BinaryExpression(
                new BinaryExpression(
                        TestUtils.numberExpression(1),
                        TestUtils.numberExpression(2),
                        BinaryExpression.Operator.PLUS
                ),
                TestUtils.numberExpression(3),
                BinaryExpression.Operator.MULTIPLY
        ));
    }
}
