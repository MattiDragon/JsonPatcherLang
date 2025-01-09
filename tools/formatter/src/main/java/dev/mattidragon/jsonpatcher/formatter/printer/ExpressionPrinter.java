package dev.mattidragon.jsonpatcher.formatter.printer;

import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import dev.mattidragon.jsonpatcher.lang.parse.Token.*;
import dev.mattidragon.jsonpatcher.lang.parse.Precedence;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;

public class ExpressionPrinter {
    public static PrintTarget prettyPrint(Expression expression, PrintTarget target) {
        if (target.isClosed()) return target;

        switch (expression) {
            case PrimitiveExpression(Value.StringValue(var value)) -> target.write(new StringToken(value));
            case PrimitiveExpression(Value.NumberValue(var value)) -> target.write(new NumberToken(value));
            case PrimitiveExpression(Value.BooleanValue value) ->
                    target.write(value.value() ? KeywordToken.TRUE : KeywordToken.FALSE);
            case PrimitiveExpression(Value.NullValue value) -> target.write(KeywordToken.NULL);
            case BinaryExpression(var first, var second, var op) -> {
                var token = switch (op) {
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
                };
                printBinaryOp(expression, first, second, token, target);
            }
            case ShortedBinaryExpression(var first, var second, var op) -> {
                var token = switch (op) {
                    case AND -> SimpleToken.DOUBLE_AND;
                    case OR -> SimpleToken.DOUBLE_OR;
                };
                printBinaryOp(expression, first, second, token, target);
            }
            case UnaryExpression(var input, var op) -> {
                var token = switch (op) {
                    case NOT -> SimpleToken.BANG;
                    case MINUS -> SimpleToken.MINUS;
                    case BITWISE_NOT -> SimpleToken.TILDE;
                    default -> new ErrorToken("Unary modification with unsupported op: " + op);
                };
                target.write(token);
                var needsParens = precedence(input) < precedence(expression);
                if (needsParens) target.write(SimpleToken.BEGIN_PAREN);
                prettyPrint(input, target);
                if (needsParens) target.write(SimpleToken.END_PAREN);
            }
            case UnaryModificationExpression(var postfix, var ref, var op) -> {
                var token = switch (op) {
                    case NOT -> SimpleToken.DOUBLE_BANG;
                    case INCREMENT -> SimpleToken.DOUBLE_MINUS;
                    case DECREMENT -> SimpleToken.DOUBLE_PLUS;
                    default -> new ErrorToken("Unary modification with unsupported op: " + op);
                };
                if (!postfix) target.write(token);
                if (!postfix) target.write(token);
            }
            case AssignmentExpression(var ref, var value, var op) -> {
                var token = switch (op) {
                    case PLUS -> SimpleToken.PLUS_ASSIGN;
                    case MINUS -> SimpleToken.MINUS_ASSIGN;
                    case MULTIPLY -> SimpleToken.STAR_ASSIGN;
                    case DIVIDE -> SimpleToken.SLASH_ASSIGN;
                    case MODULO -> SimpleToken.PERCENT_ASSIGN;
                    case AND -> SimpleToken.AND_ASSIGN;
                    case OR -> SimpleToken.OR_ASSIGN;
                    case XOR -> SimpleToken.XOR_ASSIGN;
                    case ASSIGN -> SimpleToken.ASSIGN;
                    default -> new ErrorToken("Assignment with unsupported op: " + op);
                };
                printBinaryOp(expression, ref, value, token, target);
            }
            case PropertyAccessExpression(RootExpression root, var name) ->
                    target.write(SimpleToken.DOLLAR).write(new WordToken(name));
            case PropertyAccessExpression(var parent, var name) ->
                    prettyPrint(parent, target).write(SimpleToken.DOT).write(new WordToken(name));
            case IndexExpression(var parent, var index) -> {
                prettyPrint(parent, target).write(SimpleToken.BEGIN_SQUARE);
                prettyPrint(index, target).write(SimpleToken.END_SQUARE);
            }
            case VariableAccessExpression(var name) -> target.write(new WordToken(name));
            case RootExpression() -> target.write(SimpleToken.DOLLAR);
            case ArrayInitializerExpression(var contents) -> PrintUtils.printCommaList(
                    target,
                    contents,
                    target1 -> target1.write(SimpleToken.BEGIN_SQUARE),
                    ExpressionPrinter::prettyPrint,
                    target1 -> target1.write(SimpleToken.END_SQUARE)
            );
            case ObjectInitializerExpression(var contents) -> PrintUtils.printCommaList(
                    target,
                    contents,
                    target1 -> target1.write(SimpleToken.BEGIN_CURLY),
                    (entry, target1) -> {
                        target1.write(new StringToken(entry.name()));
                        target1.write(SimpleToken.COLON).space();
                        prettyPrint(entry.value(), target1);
                    },
                    target1 -> target1.write(SimpleToken.END_CURLY)
            );
            case FunctionCallExpression(var function, var arguments) -> PrintUtils.printCommaList(
                    target,
                    arguments,
                    target1 -> prettyPrint(function, target1).write(SimpleToken.BEGIN_PAREN),
                    ExpressionPrinter::prettyPrint,
                    target1 -> target1.write(SimpleToken.END_PAREN)
            );
            default -> target.write(new ErrorToken("Unsupported expression: " + expression.getClass().getSimpleName()));
        }
        return target;
    }

    private static void printBinaryOp(Expression expression, Expression first, Expression second, Token token, PrintTarget target) {
        var firstParens = precedence(first) < precedence(expression);
        if (firstParens) target.write(SimpleToken.BEGIN_PAREN);
        prettyPrint(first, target);
        if (firstParens) target.write(SimpleToken.END_PAREN);
        target.space().write(token).space();
        var secondParens = precedence(second) < precedence(expression);
        if (secondParens) target.write(SimpleToken.BEGIN_PAREN);
        prettyPrint(second, target);
        if (secondParens) target.write(SimpleToken.END_PAREN);
    }

    private static int precedence(Expression expression) {
        return (switch (expression) {
            case AssignmentExpression e -> Precedence.ASSIGNMENT;
            case TernaryExpression e -> Precedence.ASSIGNMENT;
            case ShortedBinaryExpression(var first, var second, var op) -> switch (op) {
                case AND -> Precedence.AND;
                case OR -> Precedence.OR;
            };
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
            case IsInstanceExpression e -> Precedence.COMPARISON;
            case UnaryExpression e -> Precedence.PREFIX;
            case UnaryModificationExpression(var postfix, var target, var op) ->
                    postfix ? Precedence.POSTFIX : Precedence.PREFIX;
            case PropertyAccessExpression e -> Precedence.POSTFIX;
            case IndexExpression e -> Precedence.POSTFIX;
            case FunctionCallExpression e -> Precedence.POSTFIX;
            case PrimitiveExpression e -> Precedence.ATOM;
            case VariableAccessExpression e -> Precedence.ATOM;
            case ObjectInitializerExpression e -> Precedence.ATOM;
            case ArrayInitializerExpression e -> Precedence.ATOM;
            case RootExpression e -> Precedence.ATOM;
            case ErrorExpression e -> Precedence.ATOM;
            default -> throw new UnsupportedOperationException("Don't know precedence of " + expression);
        }).ordinal();
    }
}
