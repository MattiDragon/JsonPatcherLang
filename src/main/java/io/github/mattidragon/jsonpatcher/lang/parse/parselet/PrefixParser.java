package io.github.mattidragon.jsonpatcher.lang.parse.parselet;

import io.github.mattidragon.jsonpatcher.lang.parse.Parser;
import io.github.mattidragon.jsonpatcher.lang.parse.PositionedToken;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.parse.Token;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.*;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.ReturnStatement;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.Statement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

public class PrefixParser {
    private PrefixParser() {
    }

    private static Expression string(PositionedToken.StringToken token) {
        return new ValueExpression(new Value.StringValue(token.getToken().value()), token.getPos());
    }

    private static Expression number(PositionedToken.NumberToken token) {
        return new ValueExpression(new Value.NumberValue(token.getToken().value()), token.getPos());
    }

    private static Expression root(Parser parser, PositionedToken<?> token) {
        if (parser.hasNext() && parser.peek() instanceof PositionedToken.WordToken word) {
            parser.next();
            return new ImplicitRootExpression(word.getToken().value(), new SourceSpan(token.getFrom(), word.getTo()));
        }

        return new RootExpression(token.getPos());
    }

    private static ValueExpression constant(PositionedToken<?> token, Value.Primitive value) {
        return new ValueExpression(value, token.getPos());
    }

    private static Expression variable(PositionedToken<Token.WordToken> token) {
        return new VariableAccessExpression(token.getToken().value(), new SourceSpan(token.getFrom(), token.getTo()));
    }

    private static UnaryExpression unary(Parser parser, PositionedToken<?> token, UnaryExpression.Operator operator) {
        return new UnaryExpression(parser.expression(Precedence.PREFIX), operator, token.getPos());
    }

    private static Expression unaryModification(Parser parser, PositionedToken<?> token, UnaryExpression.Operator operator) {
        var expression = parser.expression(Precedence.PREFIX);
        if (!(expression instanceof Reference ref)) throw new Parser.ParseException("Can't modify to %s".formatted(expression), token.getPos());

        return new UnaryModificationExpression(false, ref, operator, token.getPos());
    }

    private static Expression arrayInit(Parser parser, PositionedToken<?> token) {
        var children = new ArrayList<Expression>();
        while (parser.peek().getToken() != Token.SimpleToken.END_SQUARE) {
            children.add(parser.expression());

            if (parser.peek().getToken() == Token.SimpleToken.COMMA) {
                parser.next();
            } else {
                // If there is no comma we have to be at the last element.
                break;
            }
        }
        parser.expect(Token.SimpleToken.END_SQUARE);
        return new ArrayInitializerExpression(children, new SourceSpan(token.getFrom(), parser.previous().getTo()));
    }

    private static Expression objectInit(Parser parser, PositionedToken<?> token) {
        var children = new HashMap<String, Expression>();
        while (parser.peek().getToken() != Token.SimpleToken.END_CURLY) {
            var key = parser.expectWordOrString();
            parser.expect(Token.SimpleToken.COLON);
            children.put(key, parser.expression());

            if (parser.peek().getToken() == Token.SimpleToken.COMMA) {
                parser.next();
            } else {
                // If there is no comma we have to be at the last element.
                break;
            }
        }
        parser.expect(Token.SimpleToken.END_CURLY);
        return new ObjectInitializerExpression(children, new SourceSpan(token.getFrom(), parser.previous().getTo()));
    }

    static ArrayList<Optional<String>> parseArgumentList(Parser parser) {
        var arguments = new ArrayList<Optional<String>>();
        while (parser.peek().getToken() != Token.SimpleToken.END_PAREN) {
            Optional<String> optionalArgument;
            if (parser.hasNext(Token.SimpleToken.DOLLAR)) {
                parser.next();
                optionalArgument = Optional.empty();
            } else {
                var argument = parser.expectWord().value();
                optionalArgument = Optional.of(argument);
            }

            if (arguments.contains(optionalArgument)) {
                if (optionalArgument.isPresent()) {
                    parser.addError(new Parser.ParseException("Duplicate parameter name: '%s'".formatted(optionalArgument.get()), parser.previous().getPos()));
                } else {
                    parser.addError(new Parser.ParseException("Duplicate root parameter", parser.previous().getPos()));
                }
            }
            arguments.add(optionalArgument);
            if (parser.peek().getToken() == Token.SimpleToken.COMMA) {
                parser.next();
            } else {
                break;
            }
        }
        parser.expect(Token.SimpleToken.END_PAREN);
        return arguments;
    }

    @Nullable
    private static Expression tryParseArrowFunction(Parser parser) {
        try {
            var beginPos = parser.previous().getFrom();
            var arguments = parseArgumentList(parser);
            parser.expect(Token.SimpleToken.ARROW);
            var arrowPos = parser.previous().getPos();

            Statement body = parser.hasNext(Token.SimpleToken.BEGIN_CURLY)
                    ? StatementParser.blockStatement(parser)
                    : new ReturnStatement(Optional.of(parser.expression()), arrowPos);
            return new FunctionExpression(body, arguments, new SourceSpan(beginPos, parser.previous().getTo()));
        } catch (Parser.ParseException e) {
            return null;
        }
    }

    private static Expression parenthesis(Parser parser) {
        var savedPos = parser.savePos();
        var arrowFunction = tryParseArrowFunction(parser);
        if (arrowFunction != null) {
            return arrowFunction;
        }
        parser.loadPos(savedPos);

        var expression = parser.expression();
        parser.expect(Token.SimpleToken.END_PAREN);
        return expression;
    }

    public static Expression parse(Parser parser, PositionedToken<?> token) {
        if (token instanceof PositionedToken.StringToken stringToken) return string(stringToken);
        if (token instanceof PositionedToken.NumberToken numberToken) return number(numberToken);
        if (token instanceof PositionedToken.WordToken nameToken) return variable(nameToken);
        if (token.getToken() == Token.KeywordToken.TRUE) return constant(token, Value.BooleanValue.TRUE);
        if (token.getToken() == Token.KeywordToken.FALSE) return constant(token, Value.BooleanValue.FALSE);
        if (token.getToken() == Token.KeywordToken.NULL) return constant(token, Value.NullValue.NULL);
        if (token.getToken() == Token.SimpleToken.DOLLAR) return root(parser, token);
        if (token.getToken() == Token.SimpleToken.MINUS) return unary(parser, token, UnaryExpression.Operator.MINUS);
        if (token.getToken() == Token.SimpleToken.BANG) return unary(parser, token, UnaryExpression.Operator.NOT);
        if (token.getToken() == Token.SimpleToken.TILDE) return unary(parser, token, UnaryExpression.Operator.BITWISE_NOT);
        if (token.getToken() == Token.SimpleToken.DOUBLE_MINUS) return unaryModification(parser, token, UnaryExpression.Operator.DECREMENT);
        if (token.getToken() == Token.SimpleToken.DOUBLE_PLUS) return unaryModification(parser, token, UnaryExpression.Operator.INCREMENT);
        if (token.getToken() == Token.SimpleToken.DOUBLE_BANG) return unaryModification(parser, token, UnaryExpression.Operator.NOT);
        if (token.getToken() == Token.SimpleToken.BEGIN_SQUARE) return arrayInit(parser, token);
        if (token.getToken() == Token.SimpleToken.BEGIN_CURLY) return objectInit(parser, token);
        if (token.getToken() == Token.SimpleToken.BEGIN_PAREN) return parenthesis(parser);

        throw new Parser.ParseException("Unexpected token at start of expression: %s".formatted(token.getToken()), token.getPos());
    }
}
