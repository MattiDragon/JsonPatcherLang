package dev.mattidragon.jsonpatcher.lang.parse.parselet;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.ValueType;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.parse.PositionedToken;
import dev.mattidragon.jsonpatcher.lang.parse.Precedence;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;

// Consider reworking this to use a registration or lookup system instead of a switch
public class PostfixParser {
    private PostfixParser() {}

    private static Expression parsePropertyAccess(Parser parser, Expression left, PositionedToken token) {
        var leftPos = getLeftStartPos(parser, left);
        var name = switch (parser.next().token()) {
            case Token.WordToken(String value) -> value;
            case Token.EofToken.EOF -> {
                var message = "Expected property name";
                var diagnostic = new Parser.ParseDiagnostic(token.pos(), null, message, Parser.ParseDiagnostic.Code.EOF);
                parser.addError(diagnostic);
                yield "";
            }
            case Token other -> {
                var message = "Expected property name, got %s".formatted(other.explain());
                var diagnostic = new Parser.ParseDiagnostic(token.pos(), null, message, Parser.ParseDiagnostic.Code.UNEXPECTED_TOKEN);
                parser.addError(diagnostic);
                yield "";
            }
        };
        var namePos = parser.previous().pos();
        var expression = new PropertyAccessExpression(left, name);
        parser.setMetadata(expression, MetadataKey.NAME_POS, namePos);
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(leftPos, parser.previous().to()));
        parser.setMetadata(expression, MetadataKey.KEYWORD_POS, token.pos());
        parser.setMetadata(expression, MetadataKey.MAIN_POS, namePos);
        return expression;
    }

    private static Expression parseIndexAccess(Parser parser, Expression left, PositionedToken token) {
        var leftPos = getLeftStartPos(parser, left);
        var index = parser.expression();
        parser.expect(Token.SimpleToken.END_SQUARE);
        var endPos = parser.previous().to();
        var expression = new IndexExpression(left, index);
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(leftPos, endPos));
        parser.setMetadata(expression, MetadataKey.MAIN_POS, new SourceSpan(token.from(), endPos));
        return expression;
    }

    private static Expression parseShortedBinaryOperation(Parser parser, Expression left, PositionedToken token, ShortedBinaryExpression.Operator operator, Precedence precedence) {
        var leftPos = getLeftStartPos(parser, left);
        var right = parser.expression(precedence);
        var expression = new ShortedBinaryExpression(left, right, operator);
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(leftPos, parser.previous().to()));
        parser.setMetadata(expression, MetadataKey.KEYWORD_POS, token.pos());
        return expression;
    }

    private static Expression parseBinaryOperation(Parser parser, Expression left, PositionedToken token, BinaryExpression.Operator operator, Precedence precedence) {
        var leftPos = getLeftStartPos(parser, left);
        var right = parser.expression(precedence);
        var expression = new BinaryExpression(left, right, operator);
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(leftPos, parser.previous().to()));
        parser.setMetadata(expression, MetadataKey.KEYWORD_POS, token.pos());
        return expression;
    }

    private static Expression parseUnaryModification(Parser parser, Expression left, PositionedToken token, UnaryExpression.Operator operator) {
        var leftPos = getLeftStartPos(parser, left);
        var ref = checkReference(parser, left, token.pos());
        var expression = new UnaryModificationExpression(true, ref, operator);
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(leftPos, parser.previous().to()));
        parser.setMetadata(expression, MetadataKey.KEYWORD_POS, token.pos());
        return expression;
    }

    private static Expression parseAssignment(Parser parser, Expression left, PositionedToken token, BinaryExpression.Operator operator) {
        var leftPos = getLeftStartPos(parser, left);
        var ref = checkReference(parser, left, token.pos());
        var right = parser.expression(Precedence.ROOT);
        var expression = new AssignmentExpression(ref, right, operator);
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(leftPos, parser.previous().to()));
        parser.setMetadata(expression, MetadataKey.KEYWORD_POS, token.pos());
        return expression;
    }

    public static Reference checkReference(Parser parser, Expression candidate, SourceSpan pos) {
        Reference ref;
        if (candidate instanceof Reference ref1) {
            ref = ref1;
        } else {
            var message = "%s is not assignable".formatted(candidate.getClass().getSimpleName());
            var diagnostic = new Parser.ParseDiagnostic(pos, candidate, message, Parser.ParseDiagnostic.Code.ILLEGAL_ASSIGNMENT);
            parser.addError(diagnostic);
            ref = new ErrorExpression(diagnostic, candidate);
        }
        return ref;
    }

    private static Expression parseFunctionCall(Parser parser, Expression left, PositionedToken token) {
        var leftPos = getLeftStartPos(parser, left);
        var arguments = new ArrayList<Expression>();
        while (parser.peek().token() != Token.SimpleToken.END_PAREN) {
            arguments.add(parser.expression());
            if (parser.peek().token() == Token.SimpleToken.COMMA) {
                parser.next();
            } else {
                break;
            }
        }
        parser.expect(Token.SimpleToken.END_PAREN);

        var expression = new FunctionCallExpression(left, arguments);
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(leftPos, parser.previous().to()));
        parser.setMetadata(expression, MetadataKey.MAIN_POS, new SourceSpan(token.from(), parser.previous().to()));
        return expression;
    }

    private static Expression parseIsInstance(Parser parser, Expression left, PositionedToken token) {
        var leftPos = getLeftStartPos(parser, left);
        var typeToken = parser.next().token();
        var typePos = parser.previous().pos();
        var type = getIsInstanceType(typeToken);
        Expression expression;
        if (type != null) {
            expression = new IsInstanceExpression(left, type);
        } else {
            var message = "Expected valid type name, got %s".formatted(typeToken.explain());
            var diagnostic = new Parser.ParseDiagnostic(token.pos(), null, message, Parser.ParseDiagnostic.Code.UNKNOWN_TYPE);
            parser.addError(diagnostic);
            expression = new ErrorExpression(diagnostic, left);
        }
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(leftPos, parser.previous().to()));
        parser.setMetadata(expression, MetadataKey.KEYWORD_POS, token.pos());
        parser.setMetadata(expression, MetadataKey.IS_TYPE_POS, typePos);
        return expression;
    }

    private static @Nullable ValueType getIsInstanceType(Token typeToken) {
        return switch (typeToken) {
            case Token.KeywordToken.NULL -> ValueType.NULL;
            case Token.KeywordToken.FUNCTION -> ValueType.FUNCTION;
            case Token.WordToken(var word) when word.equals("number") -> ValueType.NUMBER;
            case Token.WordToken(var word) when word.equals("string") -> ValueType.STRING;
            case Token.WordToken(var word) when word.equals("boolean") -> ValueType.BOOLEAN;
            case Token.WordToken(var word) when word.equals("array") -> ValueType.ARRAY;
            case Token.WordToken(var word) when word.equals("object") -> ValueType.OBJECT;
            case Token.WordToken(var word) when word.equals("special") -> ValueType.SPECIAL;
            default -> null;
        };
    }

    private static Expression parseTernary(Parser parser, Expression left, PositionedToken token) {
        var leftPos = getLeftStartPos(parser, left);
        var middle = parser.expression();
        parser.expect(Token.SimpleToken.COLON);
        var right = parser.expression();
        var expression = new TernaryExpression(left, middle, right);
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(leftPos, parser.previous().to()));
        parser.setMetadata(expression, MetadataKey.KEYWORD_POS, token.pos());
        return expression;
    }
    
    private static SourcePos getLeftStartPos(Parser parser, Expression left) {
        return parser.getMetadata(left, MetadataKey.FULL_POS)
                .or(() -> parser.getMetadata(left, MetadataKey.MAIN_POS))
                .map(SourceSpan::from)
                .orElseGet(() -> parser.previous().from());
    }

    public static @Nullable Expression get(Parser parser, Precedence precedence, Expression left) {
        var token = parser.peek();
        if (token instanceof PositionedToken(var pos, Token.KeywordToken keywordToken) && precedence.ordinal() <= Precedence.COMPARISON.ordinal()) {
            if (keywordToken == Token.KeywordToken.IS) {
                return parseIsInstance(parser, left, parser.next());
            }
            if (keywordToken == Token.KeywordToken.IN) {
                return parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.IN, Precedence.COMPARISON);
            }
        }

        if (!(token.token() instanceof Token.SimpleToken simpleToken)) return null;

        // abuse fallthrough to check precedence levels in order
        switch (precedence) {
            case ROOT:
            case ASSIGNMENT: {
                var expression = switch (simpleToken) {
                    case ASSIGN -> parseAssignment(parser, left, parser.next(), BinaryExpression.Operator.ASSIGN);
                    case PLUS_ASSIGN -> parseAssignment(parser, left, parser.next(), BinaryExpression.Operator.PLUS);
                    case MINUS_ASSIGN -> parseAssignment(parser, left, parser.next(), BinaryExpression.Operator.MINUS);
                    case STAR_ASSIGN -> parseAssignment(parser, left, parser.next(), BinaryExpression.Operator.MULTIPLY);
                    case SLASH_ASSIGN -> parseAssignment(parser, left, parser.next(), BinaryExpression.Operator.DIVIDE);
                    case PERCENT_ASSIGN -> parseAssignment(parser, left, parser.next(), BinaryExpression.Operator.MODULO);
                    case OR_ASSIGN -> parseAssignment(parser, left, parser.next(), BinaryExpression.Operator.OR);
                    case XOR_ASSIGN -> parseAssignment(parser, left, parser.next(), BinaryExpression.Operator.XOR);
                    case AND_ASSIGN -> parseAssignment(parser, left, parser.next(), BinaryExpression.Operator.AND);
                    case QUESTION_MARK -> parseTernary(parser, left, parser.next());
                    default -> null;
                };
                if (expression != null) return expression;
            }
            case OR:
                if (token.token() == Token.SimpleToken.DOUBLE_OR) {
                    return parseShortedBinaryOperation(parser, left, parser.next(), ShortedBinaryExpression.Operator.OR, Precedence.OR);
                }
            case AND:
                if (token.token() == Token.SimpleToken.DOUBLE_AND) {
                    return parseShortedBinaryOperation(parser, left, parser.next(), ShortedBinaryExpression.Operator.AND, Precedence.AND);
                }
            case BITWISE_OR:
                if (token.token() == Token.SimpleToken.OR) {
                    return parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.OR, Precedence.BITWISE_OR);
                }
            case BITWISE_XOR:
                if (token.token() == Token.SimpleToken.XOR) {
                    return parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.XOR, Precedence.BITWISE_XOR);
                }
            case BITWISE_AND:
                if (token.token() == Token.SimpleToken.AND) {
                    return parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.AND, Precedence.BITWISE_AND);
                }
            case EQUALITY: {
                var expression = switch (simpleToken) {
                    case EQUALS -> parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.EQUALS, Precedence.EQUALITY);
                    case NOT_EQUALS -> parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.NOT_EQUALS, Precedence.EQUALITY);
                    default -> null;
                };
                if (expression != null) return expression;
            }
            case COMPARISON: {
                var expression = switch (simpleToken) {
                    case LESS_THAN -> parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.LESS_THAN, Precedence.COMPARISON);
                    case LESS_THAN_EQUAL -> parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.LESS_THAN_EQUAL, Precedence.COMPARISON);
                    case GREATER_THAN -> parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.GREATER_THAN, Precedence.COMPARISON);
                    case GREATER_THAN_EQUAL -> parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.GREATER_THAN_EQUAL, Precedence.COMPARISON);
                    default -> null;
                };
                if (expression != null) return expression;
            }
            case BIT_SHIFT:
            case SUM: {
                var expression = switch (simpleToken) {
                    case PLUS -> parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.PLUS, Precedence.SUM);
                    case MINUS -> parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.MINUS, Precedence.SUM);
                    default -> null;
                };
                if (expression != null) return expression;
            }
            case PRODUCT: {
                var expression = switch (simpleToken) {
                    case STAR -> parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.MULTIPLY, Precedence.PRODUCT);
                    case SLASH -> parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.DIVIDE, Precedence.PRODUCT);
                    case PERCENT -> parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.MODULO, Precedence.PRODUCT);
                    default -> null;
                };
                if (expression != null) return expression;
            }
            case EXPONENT: {
                if (simpleToken == Token.SimpleToken.DOUBLE_STAR) {
                    return parseBinaryOperation(parser, left, parser.next(), BinaryExpression.Operator.EXPONENT, Precedence.EXPONENT);
                }
            }
            case PREFIX:
            case POSTFIX: {
                var expression = switch (simpleToken) {
                    case DOT -> parsePropertyAccess(parser, left, parser.next());
                    case BEGIN_SQUARE -> parseIndexAccess(parser, left, parser.next());
                    case BEGIN_PAREN -> parseFunctionCall(parser, left, parser.next());
                    case DOUBLE_MINUS -> parseUnaryModification(parser, left, parser.next(), UnaryExpression.Operator.DECREMENT);
                    case DOUBLE_PLUS -> parseUnaryModification(parser, left, parser.next(), UnaryExpression.Operator.INCREMENT);
                    case DOUBLE_BANG -> parseUnaryModification(parser, left, parser.next(), UnaryExpression.Operator.NOT);
                    default -> null;
                };
                if (expression != null) return expression;
            }
            default:
                if (simpleToken == Token.SimpleToken.ARROW) {
                    parser.addError(parser.next().pos(), "Unexpected arrow, did you mean to put parentheses around your function arguments?", Parser.ParseDiagnostic.Code.UNEXPECTED_TOKEN);
                }
                return null;
        }
    }
}
