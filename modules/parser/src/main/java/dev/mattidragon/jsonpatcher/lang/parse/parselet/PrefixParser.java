package dev.mattidragon.jsonpatcher.lang.parse.parselet;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.*;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArgument;
import dev.mattidragon.jsonpatcher.lang.ast.function.FunctionArguments;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.statement.ReturnStatement;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.lang.parse.PositionedToken;
import dev.mattidragon.jsonpatcher.lang.parse.Precedence;
import dev.mattidragon.jsonpatcher.lang.parse.Token;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

public class PrefixParser {
    private PrefixParser() {
    }

    private static Expression string(Parser parser, SourceSpan pos, Token.StringToken token) {
        var expression = new PrimitiveExpression(new Value.StringValue(token.value()));
        parser.setMetadata(expression, MetadataKey.FULL_POS, pos);
        return expression;
    }

    private static Expression number(Parser parser, SourceSpan pos, Token.NumberToken token) {
        var expression = new PrimitiveExpression(new Value.NumberValue(token.value()));
        parser.setMetadata(expression, MetadataKey.FULL_POS, pos);
        return expression;
    }

    private static Expression root(Parser parser, PositionedToken token) {
        var rootExpression = parser.setMetadata(new RootExpression(), MetadataKey.FULL_POS, token.pos());

        if (parser.hasNext() && parser.peek() instanceof PositionedToken(var pos, Token.WordToken word)) {
            parser.next();
            return parser.setMetadata(
                    new PropertyAccessExpression(rootExpression, word.value()),
                    MetadataKey.NAME_POS,
                    pos
            );
        }

        return rootExpression;
    }

    private static PrimitiveExpression constant(Parser parser, PositionedToken token, Value.Primitive value) {
        var expression = new PrimitiveExpression(value);
        parser.setMetadata(expression, MetadataKey.FULL_POS, token.pos());
        return expression;
    }

    private static Expression variable(Parser parser, SourceSpan pos, Token.WordToken token) {
        var expression = new VariableAccessExpression(token.value());
        parser.setMetadata(expression, MetadataKey.FULL_POS, pos);
        return expression;
    }

    private static UnaryExpression unary(Parser parser, PositionedToken token, UnaryExpression.Operator operator) {
        var expression = new UnaryExpression(parser.expression(Precedence.PREFIX), operator);
        parser.setMetadata(expression, MetadataKey.FULL_POS, token.pos());
        return expression;
    }

    private static Expression unaryModification(Parser parser, PositionedToken token, UnaryExpression.Operator operator) {
        var inside = parser.expression(Precedence.PREFIX);
        if (!(inside instanceof Reference ref)) throw parser.new ParseException("Can't modify to %s".formatted(inside), token.pos());

        var expression = new UnaryModificationExpression(false, ref, operator);
        parser.setMetadata(expression, MetadataKey.FULL_POS, token.pos());
        return expression;
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
        var expression = new ArrayInitializerExpression(children);
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(token.from(), parser.previous().to()));
        return expression;
    }

    private static Expression objectInit(Parser parser, PositionedToken token) {
        var children = new ArrayList<ObjectInitializerExpression.Entry>();
        while (parser.peek().token() != Token.SimpleToken.END_CURLY) {
            var key = parser.expectWordOrString(); // TODO: This is stupid, deprecate string keys, we already have quoted words
            var keyPos = parser.previous().pos();
            parser.expect(Token.SimpleToken.COLON);

            var entry = new ObjectInitializerExpression.Entry(key, parser.expression());
            parser.setMetadata(entry, MetadataKey.FULL_POS, keyPos);
            children.add(entry);
            
            if (parser.peek().token() == Token.SimpleToken.END_CURLY) {
                break;
            }
            parser.expectSoftly(Token.SimpleToken.COMMA);
        }
        parser.expect(Token.SimpleToken.END_CURLY);
        var expression = new ObjectInitializerExpression(children);
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(token.from(), parser.previous().to()));
        return expression;
    }

    static FunctionArguments parseArgumentList(Parser parser) {
        var fromPos = parser.previous().from();
        
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
                if (target instanceof FunctionArgument.Target.Variable(var paramName)) {
                    parser.addError(parser.new ParseException("Duplicate parameter name: '%s'".formatted(paramName), parser.previous().pos()));
                } else {
                    parser.addError(parser.new ParseException("Duplicate root parameter", parser.previous().pos()));
                }
            }
            targets.add(target);
            
            var defaultValue = Optional.<Expression>empty();
            if (parser.peek().token() == Token.SimpleToken.STAR) {
                varargsPos = parser.next().pos();
                varargs = true;
                defaultValue = Optional.of(new ArrayInitializerExpression(List.of()));
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
                parser.addError(parser.new ParseException("All required arguments must appear before optional arguments", parser.previous().pos()));
            }

            var argument = new FunctionArgument(target, defaultValue);
            parser.setMetadata(argument, MetadataKey.NAME_POS, namePos);
            parser.setMetadata(argument, MetadataKey.FULL_POS, new SourceSpan(namePos.from(), parser.previous().to()));
            arguments.add(argument);

            if (parser.peek().token() == Token.SimpleToken.END_PAREN) {
                break;
            }
            parser.expectSoftly(Token.SimpleToken.COMMA);
        }
        parser.expect(Token.SimpleToken.END_PAREN);
        var functionArguments = new FunctionArguments(arguments, varargs);
        parser.setMetadata(functionArguments, MetadataKey.FULL_POS, new SourceSpan(fromPos, parser.previous().to()));
        return functionArguments;
    }

    @Nullable
    private static Expression tryParseArrowFunction(Parser parser) {
        try {
            var beginPos = parser.previous().from();
            var arguments = parseArgumentList(parser);
            parser.expect(Token.SimpleToken.ARROW);
            var arrowPos = parser.previous().pos();

            var body = parser.hasNext(Token.SimpleToken.BEGIN_CURLY)
                    ? StatementParser.blockStatement(parser)
                    : parser.setMetadata(new ReturnStatement(Optional.of(parser.expression())), MetadataKey.KEYWORD_POS, arrowPos);
            var expression = new FunctionExpression(body, arguments);
            parser.setMetadata(expression, MetadataKey.KEYWORD_POS, arrowPos);
            parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(beginPos, parser.previous().to()));
            return expression;
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
            case Token.StringToken stringToken -> string(parser, pos, stringToken);
            case Token.NumberToken numberToken -> number(parser, pos, numberToken);
            case Token.WordToken wordToken -> variable(parser, pos, wordToken);

            case Token.KeywordToken.TRUE -> constant(parser, token, Value.BooleanValue.TRUE);
            case Token.KeywordToken.FALSE -> constant(parser, token, Value.BooleanValue.FALSE);
            case Token.KeywordToken.NULL -> constant(parser, token, Value.NullValue.NULL);
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
