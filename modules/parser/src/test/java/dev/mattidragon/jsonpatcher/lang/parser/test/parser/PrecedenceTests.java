package dev.mattidragon.jsonpatcher.lang.parser.test.parser;

import dev.mattidragon.jsonpatcher.lang.test.TestUtils;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.BinaryExpression;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.ShortedBinaryExpression;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.UnaryExpression;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PrecedenceTests {
    @Test
    public void testSumProductPrecedence() {
        Assertions.assertEquals(new BinaryExpression(
                TestUtils.numberExpression(1),
                new BinaryExpression(
                        TestUtils.numberExpression(2),
                        TestUtils.numberExpression(3),
                        BinaryExpression.Operator.MULTIPLY),
                BinaryExpression.Operator.PLUS
        ), TestUtils.parseExpression("1 + 2 * 3"));
        
        Assertions.assertEquals(new BinaryExpression(
                new BinaryExpression(
                        TestUtils.numberExpression(1),
                        TestUtils.numberExpression(2),
                        BinaryExpression.Operator.MULTIPLY),
                TestUtils.numberExpression(3),
                BinaryExpression.Operator.PLUS
        ), TestUtils.parseExpression("1 * 2 + 3"));
    }

    @Test
    public void testLogicEqualityPrecedence() {
        Assertions.assertEquals(new ShortedBinaryExpression(
                new BinaryExpression(
                        TestUtils.trueExpression(),
                        TestUtils.falseExpression(),
                        BinaryExpression.Operator.EQUALS),
                TestUtils.trueExpression(),
                ShortedBinaryExpression.Operator.AND
        ), TestUtils.parseExpression("true == false && true"));
        
        Assertions.assertEquals(new ShortedBinaryExpression(
                TestUtils.trueExpression(),
                new BinaryExpression(
                        TestUtils.trueExpression(),
                        TestUtils.falseExpression(),
                        BinaryExpression.Operator.NOT_EQUALS),
                ShortedBinaryExpression.Operator.AND
        ), TestUtils.parseExpression("true && true != false"));
    }

    @Test
    public void testPrefixPrecedence() {
        Assertions.assertEquals(new BinaryExpression(
                new UnaryExpression(TestUtils.trueExpression(), UnaryExpression.Operator.NOT),
                TestUtils.falseExpression(),
                BinaryExpression.Operator.EQUALS
        ), TestUtils.parseExpression("!true == false"));


        Assertions.assertEquals(new BinaryExpression(
                new UnaryExpression(TestUtils.numberExpression(1), UnaryExpression.Operator.MINUS),
                new UnaryExpression(TestUtils.numberExpression(2), UnaryExpression.Operator.MINUS),
                BinaryExpression.Operator.MINUS
        ), TestUtils.parseExpression("-1 - -2"));
    }
}
