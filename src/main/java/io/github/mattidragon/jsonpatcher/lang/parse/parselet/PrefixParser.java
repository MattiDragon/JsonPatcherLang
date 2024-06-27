package io.github.mattidragon.jsonpatcher.lang.parse.parselet;

import io.github.mattidragon.jsonpatcher.lang.parse.Parser;
import io.github.mattidragon.jsonpatcher.lang.parse.PositionedToken;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.parse.Token;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.*;
import io.github.mattidragon.jsonpatcher.lang.runtime.function.FunctionArgument;
import io.github.mattidragon.jsonpatcher.lang.runtime.function.FunctionArguments;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.ReturnStatement;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.Statement;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class PrefixParser {
    private PrefixParser() {
    }

    private static Expression string(SourceSpan pos, Token.StringToken token) {
        return new ValueExpression(new Value.StringValue(token.value()), pos);
    }

    private static Expression number(SourceSpan pos, Token.NumberToken token) {
        return new ValueExpression(new Value.NumberValue(token.value()), pos);
    }

    private static Expression root(Parser parser, PositionedToken token) {
        if (parser.hasNext() && parser.peek() instanceof PositionedToken(var pos, Token.WordToken word)) {
            parser.next();
            return new PropertyAccessExpression(new RootExpression(token.pos()), word.value(), pos, pos);
        }

        return new RootExpression(token.pos());
    }

    private static ValueExpression constant(PositionedToken token, Value.Primitive value) {
        return new ValueExpression(value, token.pos());
    }

    private static Expression variable(SourceSpan pos, Token.WordToken token) {
        return new VariableAccessExpression(token.value(), pos);
    }

    private static UnaryExpression unary(Parser parser, PositionedToken token, UnaryExpression.Operator operator) {
        return new UnaryExpression(parser.expression(Precedence.PREFIX), operator, token.pos());
    }

    private static Expression unaryModification(Parser parser, PositionedToken token, UnaryExpression.Operator operator) {
        var expression = parser.expression(Precedence.PREFIX);
        if (!(expression instanceof Reference ref)) throw parser.new ParseException("Can't modify to %s".formatted(expression), token.pos());

        return new UnaryModificationExpression(false, ref, operator, token.pos());
    }

    private static Expression arrayInit(Parser parser, PositionedToken token) {
        var children = new ArrayList<Expression>();
        while (parser.peek().token() != Token.SimpleToken.END_SQUARE) {
            children.add(parser.expression());

            if (parser.peek().token() == Token.SimpleToken.END_SQUARE) {
                break;
            }
            parser.expectSoftly(Token.SimpleToken.COMMA);
        }
        parser.expect(Token.SimpleToken.END_SQUARE);
        return new ArrayInitializerExpression(children, new SourceSpan(token.getFrom(), parser.previous().getTo()));
    }

    private static Expression objectInit(Parser parser, PositionedToken token) {
        var children = new ArrayList<ObjectInitializerExpression.Entry>();
        while (parser.peek().token() != Token.SimpleToken.END_CURLY) {
            var key = parser.expectWordOrString();
            var keyPos = parser.previous().pos();
            parser.expect(Token.SimpleToken.COLON);
            children.add(new ObjectInitializerExpression.Entry(key, keyPos, parser.expression()));
            
            if (parser.peek().token() == Token.SimpleToken.END_CURLY) {
                break;
            }
            parser.expectSoftly(Token.SimpleToken.COMMA);
        }
        parser.expect(Token.SimpleToken.END_CURLY);
        return new ObjectInitializerExpression(children, new SourceSpan(token.getFrom(), parser.previous().getTo()));
    }

    static FunctionArguments parseArgumentList(Parser parser) {
        var targets = new HashSet<FunctionArgument.Target>();
        var arguments = new ArrayList<FunctionArgument>();
        // Position of the last varargs argument. Used in the error if there are more arguments.
        SourceSpan varargsPos = null;
        var varargs = false;
        var optionalArg = false;
        
        while (parser.peek().token() != Token.SimpleToken.END_PAREN) {
            // If we end up here with the varargs flag set we are trying to parse an argument after the varargs argument
            if (varargs) {
                parser.addError(parser.new ParseException("Varargs parameter must be last in list", varargsPos));
                varargs = false;
            }
            
            FunctionArgument.Target target;
            if (parser.hasNext(Token.SimpleToken.DOLLAR)) {
                parser.next();
                target = FunctionArgument.Target.Root.INSTANCE;
            } else {
                var argumentName = parser.expectWord().value();
                target = new FunctionArgument.Target.Variable(argumentName);
            }
            var namePos = parser.previous().pos();

            if (targets.contains(target)) {
                if (target instanceof FunctionArgument.Target.Variable variable) {
                    PositionedToken positionedToken = parser.previous();
                    parser.addError(parser.new ParseException("Duplicate parameter name: '%s'".formatted(variable.name()), positionedToken.pos()));
                } else {
                    PositionedToken positionedToken = parser.previous();
                    parser.addError(parser.new ParseException("Duplicate root parameter", positionedToken.pos()));
                }
            }
            targets.add(target);
            
            var defaultValue = Optional.<Expression>empty();
            if (parser.peek().token() == Token.SimpleToken.STAR) {
                PositionedToken positionedToken1 = parser.next();
                varargsPos = positionedToken1.pos();
                varargs = true;
                defaultValue = Optional.of(new ArrayInitializerExpression(List.of(), varargsPos));
                optionalArg = true;
                if (parser.peek().token() == Token.SimpleToken.ASSIGN) {
                    parser.addError(parser.new ParseException("Varargs parameter cannot have default value", parser.peek().pos()));
                }
            }
            // We parse default values after varargs to avoid garbage errors. 
            // This won't ever actually be used because we add an error above.
            if (parser.peek().token() == Token.SimpleToken.ASSIGN) {
                parser.next();
                defaultValue = Optional.of(parser.expression());
                optionalArg = true;
            } 
            if (defaultValue.isEmpty() && optionalArg) {
                PositionedToken positionedToken = parser.previous();
                parser.addError(parser.new ParseException("All required arguments must appear before optional arguments", positionedToken.pos()));
            }
            
            arguments.add(new FunctionArgument(target, defaultValue, namePos));

            if (parser.peek().token() == Token.SimpleToken.END_PAREN) {
                break;
            }
            parser.expectSoftly(Token.SimpleToken.COMMA);
        }
        parser.expect(Token.SimpleToken.END_PAREN);
        return new FunctionArguments(arguments, varargs);
    }

    @Nullable
    private static Expression tryParseArrowFunction(Parser parser) {
        try {
            var beginPos = parser.previous().getFrom();
            var arguments = parseArgumentList(parser);
            parser.expect(Token.SimpleToken.ARROW);
            PositionedToken positionedToken = parser.previous();
            var arrowPos = positionedToken.pos();

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

    public static Expression parse(Parser parser, PositionedToken token) {
        var pos = token.pos();
        return switch (token.token()) {
            case Token.StringToken stringToken -> string(pos, stringToken);
            case Token.NumberToken numberToken -> number(pos, numberToken);
            case Token.WordToken wordToken -> variable(pos, wordToken);

            case Token.KeywordToken.TRUE -> constant(token, Value.BooleanValue.TRUE);
            case Token.KeywordToken.FALSE -> constant(token, Value.BooleanValue.FALSE);
            case Token.KeywordToken.NULL -> constant(token, Value.NullValue.NULL);
            case Token.SimpleToken.DOLLAR -> root(parser, token);
            case Token.SimpleToken.MINUS -> unary(parser, token, UnaryExpression.Operator.MINUS);
            case Token.SimpleToken.BANG -> unary(parser, token, UnaryExpression.Operator.NOT);
            case Token.SimpleToken.TILDE -> unary(parser, token, UnaryExpression.Operator.BITWISE_NOT);
            case Token.SimpleToken.DOUBLE_MINUS -> unaryModification(parser, token, UnaryExpression.Operator.DECREMENT);
            case Token.SimpleToken.DOUBLE_PLUS -> unaryModification(parser, token, UnaryExpression.Operator.INCREMENT);
            case Token.SimpleToken.DOUBLE_BANG -> unaryModification(parser, token, UnaryExpression.Operator.NOT);
            case Token.SimpleToken.BEGIN_SQUARE -> arrayInit(parser, token);
            case Token.SimpleToken.BEGIN_CURLY -> objectInit(parser, token);
            case Token.SimpleToken.BEGIN_PAREN -> parenthesis(parser);
            
            case Token other -> throw parser.new ParseException("Unexpected token at start of expression: %s".formatted(other.explain()), pos);
        };
    }
}
