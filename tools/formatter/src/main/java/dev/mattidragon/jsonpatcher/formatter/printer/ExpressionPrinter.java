package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import dev.mattidragon.jsonpatcher.lang.parse.Token.KeywordToken;
import dev.mattidragon.jsonpatcher.lang.parse.Token.NumberToken;
import dev.mattidragon.jsonpatcher.lang.parse.Token.SimpleToken;
import dev.mattidragon.jsonpatcher.lang.parse.Token.StringToken;
import dev.mattidragon.jsonpatcher.lang.parse.parselet.Precedence;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;

public class ExpressionPrinter {
    public static PrintTarget prettyPrint(Expression expression, PrintTarget target) {
        if (target.isClosed()) return target;

        switch (expression) {
            case ValueExpression(Value.StringValue(var value)) -> target.write(new StringToken(value));
            case ValueExpression(Value.NumberValue(var value)) -> target.write(new NumberToken(value));
            case ValueExpression(Value.BooleanValue value) ->
                    target.write(value.value() ? KeywordToken.TRUE : KeywordToken.FALSE);
            case ValueExpression(Value.NullValue value) -> target.write(KeywordToken.NULL);
            case BinaryExpression(var first, var second, var op) -> {
                var firstParens = precedence(first) < precedence(expression);
                if (firstParens) target.write(SimpleToken.BEGIN_PAREN);
                prettyPrint(first, target);
                if (firstParens) target.write(SimpleToken.END_PAREN);
                target.space().write(switch (op) {
                    case PLUS -> SimpleToken.PLUS;
                    case MINUS -> SimpleToken.MINUS;
                    case MULTIPLY -> SimpleToken.STAR;
                    case DIVIDE -> SimpleToken.SLASH;
                    case MODULO -> SimpleToken.PERCENT;
                    case EXPONENT -> SimpleToken.DOUBLE_STAR;
                    case AND -> SimpleToken.AND;
                    case OR -> SimpleToken.OR;
                    case XOR -> SimpleToken.XOR;
                    case EQUALS -> SimpleToken.EQUALS;
                    case NOT_EQUALS -> SimpleToken.NOT_EQUALS;
                    case LESS_THAN -> SimpleToken.LESS_THAN;
                    case GREATER_THAN -> SimpleToken.GREATER_THAN;
                    case LESS_THAN_EQUAL -> SimpleToken.LESS_THAN_EQUAL;
                    case GREATER_THAN_EQUAL -> SimpleToken.GREATER_THAN_EQUAL;
                    case IN -> KeywordToken.IN;
                    case ASSIGN -> SimpleToken.ASSIGN;
                }).space();
                var secondParens = precedence(second) < precedence(expression);
                if (secondParens) target.write(SimpleToken.BEGIN_PAREN);
                prettyPrint(second, target);
                if (secondParens) target.write(SimpleToken.END_PAREN);
            }
            case PropertyAccessExpression(RootExpression root, var name) ->
                    target.write(SimpleToken.DOLLAR).write(new Token.WordToken(name));
            case PropertyAccessExpression(var parent, var name) ->
                    prettyPrint(parent, target).write(SimpleToken.DOT).write(new Token.WordToken(name));
            case IndexExpression(var parent, var index) -> {
                prettyPrint(parent, target).write(SimpleToken.BEGIN_SQUARE);
                prettyPrint(index, target).write(SimpleToken.END_SQUARE);
            }
            case VariableAccessExpression(var name) -> target.write(new StringToken(name));
            case RootExpression() -> target.write(SimpleToken.DOLLAR);
            case ArrayInitializerExpression(var contents) -> PrintUtils.printWithMultilineOption(
                    target,
                    inlineTarget -> {
                        inlineTarget.write(SimpleToken.BEGIN_SQUARE);
                        for (int i = 0; i < contents.size(); i++) {
                            if (i != 0) {
                                inlineTarget.write(SimpleToken.COMMA);
                            }
                            prettyPrint(contents.get(i), inlineTarget);
                        }
                        inlineTarget.write(SimpleToken.END_SQUARE);
                    },
                    multilineTarget -> {
                        multilineTarget.write(SimpleToken.BEGIN_SQUARE);
                        multilineTarget.pushIndent().newLine();
                        for (int i = 0; i < contents.size(); i++) {
                            if (i != 0) {
                                multilineTarget.write(SimpleToken.COMMA);
                                multilineTarget.newLine();
                            }
                            prettyPrint(contents.get(i), multilineTarget);
                        }
                        multilineTarget.popIndent().newLine();
                        multilineTarget.write(SimpleToken.END_SQUARE);
                    });
            case ObjectInitializerExpression(var contents) -> PrintUtils.printWithMultilineOption(
                    target,
                    inlineTarget -> {
                        inlineTarget.write(SimpleToken.BEGIN_CURLY);
                        for (int i = 0; i < contents.size(); i++) {
                            if (i != 0) {
                                inlineTarget.write(SimpleToken.COMMA);
                            }
                            var entry = contents.get(i);
                            inlineTarget.write(new Token.WordToken(entry.name()))
                                    .write(SimpleToken.COLON).space();
                            prettyPrint(entry.value(), inlineTarget);
                        }
                        inlineTarget.write(SimpleToken.END_CURLY);
                    },
                    multilineTarget -> {
                        multilineTarget.write(SimpleToken.BEGIN_CURLY);
                        multilineTarget.pushIndent().newLine();
                        for (int i = 0; i < contents.size(); i++) {
                            if (i != 0) {
                                multilineTarget.write(SimpleToken.COMMA);
                                multilineTarget.newLine();
                            }
                            var entry = contents.get(i);
                            multilineTarget.write(new Token.WordToken(entry.name()))
                                    .write(SimpleToken.COLON).space();
                            prettyPrint(entry.value(), multilineTarget);
                        }
                        multilineTarget.popIndent().newLine();
                        multilineTarget.write(SimpleToken.END_CURLY);
                    });
            default -> target.writeText("('no impl')");
        }
        return target;
    }

    private static int precedence(Expression expression) {
        return (switch (expression) {
            case UnaryModificationExpression(var postfix, var target, var op) ->
                    postfix ? Precedence.POSTFIX : Precedence.PREFIX;
            case BinaryExpression(var first, var second, var op) -> switch (op) {
                case PLUS, MINUS -> Precedence.SUM;
                case MULTIPLY, MODULO, DIVIDE -> Precedence.PRODUCT;
                case EXPONENT -> Precedence.EXPONENT;
                case AND -> Precedence.BITWISE_AND;
                case OR -> Precedence.BITWISE_OR;
                case XOR -> Precedence.BITWISE_XOR;
                case EQUALS, NOT_EQUALS -> Precedence.EQUALITY;
                case LESS_THAN, ASSIGN, IN, GREATER_THAN_EQUAL, LESS_THAN_EQUAL, GREATER_THAN -> Precedence.COMPARISON;
            };
            default -> Precedence.ROOT;
        }).ordinal();
    }
}
