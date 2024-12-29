package dev.mattidragon.jsonpatcher.formatter.test;

import dev.mattidragon.jsonpatcher.lang.ast.expression.AssignmentExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.BinaryExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.VariableAccessExpression;
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

    @Test
    public void testAssignmentPrecedence() {
        FormatValidator.validate(new AssignmentExpression(
                new VariableAccessExpression("a"),
                new AssignmentExpression(
                        new VariableAccessExpression("b"),
                        new BinaryExpression(
                                TestUtils.falseExpression(),
                                TestUtils.trueExpression(),
                                BinaryExpression.Operator.AND
                        ),
                        BinaryExpression.Operator.OR
                ),
                BinaryExpression.Operator.ASSIGN
        ));
    }
}
