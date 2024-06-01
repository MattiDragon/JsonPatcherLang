package io.github.mattidragon.jsonpatcher.lang.parse.parselet;

import io.github.mattidragon.jsonpatcher.lang.parse.Parser;
import io.github.mattidragon.jsonpatcher.lang.parse.PositionedToken;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.parse.Token;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.Expression;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.FunctionExpression;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.Reference;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.ValueExpression;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.*;

import java.util.ArrayList;
import java.util.Optional;

import static io.github.mattidragon.jsonpatcher.lang.parse.Token.KeywordToken;
import static io.github.mattidragon.jsonpatcher.lang.parse.Token.SimpleToken;

public class StatementParser {
    private StatementParser() {
    }

    static Statement blockStatement(Parser parser) {
        parser.expect(SimpleToken.BEGIN_CURLY);
        var beginPos = parser.previous().getFrom();
        var statements = new ArrayList<Statement>();
        try {
            while (parser.peek().token() != SimpleToken.END_CURLY) {
                statements.add(parse(parser));
            }
        } catch (Parser.ParseException e) {
            parser.addError(e);
            parser.seek(SimpleToken.END_CURLY);
            return new ErrorStatement(e);
        }
        parser.expect(SimpleToken.END_CURLY);
        var endPos = parser.previous().getTo();
        return new BlockStatement(statements, new SourceSpan(beginPos, endPos));
    }

    private static Statement applyStatement(Parser parser) {
        parser.expect(KeywordToken.APPLY);
        var beginPos = parser.previous().getFrom();
        parser.expect(SimpleToken.BEGIN_PAREN);
        var root = parser.expression();
        parser.expect(SimpleToken.END_PAREN);
        var action = parse(parser);
        var endPos = parser.previous().getTo();
        return new ApplyStatement(root, action, new SourceSpan(beginPos, endPos));
    }

    private static Statement ifStatement(Parser parser) {
        parser.expect(KeywordToken.IF);
        var beginPos = parser.previous().getFrom();
        parser.expect(SimpleToken.BEGIN_PAREN);
        var condition = parser.expression();
        parser.expect(SimpleToken.END_PAREN);
        var action = parse(parser);
        Statement elseAction = null;
        if (parser.hasNext(KeywordToken.ELSE)) {
            parser.next();
            elseAction = parse(parser);
        }
        var endPos = parser.previous().getTo();
        return new IfStatement(condition, action, elseAction, new SourceSpan(beginPos, endPos));
    }

    private static Statement variableStatement(Parser parser, boolean mutable) {
        var begin = parser.next().getFrom();
        var name = parser.expectWord();
        var namePos = parser.previous().pos();
        parser.expect(SimpleToken.ASSIGN);
        var initializer = parser.expression();
        parser.expect(SimpleToken.SEMICOLON);
        return new VariableCreationStatement(name.value(), initializer, mutable, new SourceSpan(begin, parser.previous().getTo()), namePos);
    }

    private static Statement deleteStatement(Parser parser) {
        var begin = parser.next().getFrom();
        var expression = parser.expression();
        if (!(expression instanceof Reference ref)) throw new Parser.ParseException("Can't delete to %s".formatted(expression), expression.pos());
        parser.expect(SimpleToken.SEMICOLON);
        return new DeleteStatement(ref, new SourceSpan(begin, parser.previous().getTo()));
    }

    private static Statement returnStatement(Parser parser) {
        var begin = parser.next().getFrom();
        Optional<Expression> value;
        PositionedToken positionedToken = parser.peek();
        if (positionedToken.token() == SimpleToken.SEMICOLON) {
            value = Optional.empty();
        } else {
            value = Optional.of(parser.expression());
        }
        parser.expect(SimpleToken.SEMICOLON);
        return new ReturnStatement(value, new SourceSpan(begin, parser.previous().getTo()));
    }

    private static FunctionDeclarationStatement functionDeclaration(Parser parser) {
        parser.expect(KeywordToken.FUNCTION);
        var begin = parser.previous().getFrom();
        
        var name = parser.expectWord().value();
        var namePos = parser.previous().pos();

        parser.expect(SimpleToken.BEGIN_PAREN);
        var arguments = PrefixParser.parseArgumentList(parser);
        var body = blockStatement(parser);
        var expression = new FunctionExpression(body, arguments, new SourceSpan(begin, parser.previous().getTo()));

        return new FunctionDeclarationStatement(name, expression, namePos);
    }

    private static Statement expressionStatement(Parser parser) {
        Expression expression;
        try {
            expression = parser.expression();
        } catch (Parser.ParseException e) {
            parser.addError(e);
            parser.seek(SimpleToken.SEMICOLON);
            return new ErrorStatement(e);
        }
        
        var lastTokenPos = parser.previous().pos().to();
        if (parser.next().token() != SimpleToken.SEMICOLON) {
            throw new Parser.ParseException("Semicolon expected", new SourceSpan(lastTokenPos, lastTokenPos));
        }
        
        return new ExpressionStatement(expression);
    }

    private static Statement whileLoop(Parser parser) {
        parser.expect(KeywordToken.WHILE);
        var from = parser.previous().getFrom();
        parser.expect(SimpleToken.BEGIN_PAREN);
        var condition = parser.expression();
        parser.expect(SimpleToken.END_PAREN);
        var to = parser.previous().getTo();
        var body = parse(parser);

        return new WhileLoopStatement(condition, body, new SourceSpan(from, to));
    }

    private static Statement forLoop(Parser parser) {
        parser.expect(KeywordToken.FOR);
        var from = parser.previous().getFrom();
        parser.expect(SimpleToken.BEGIN_PAREN);

        Statement initializer;
        if (parser.hasNext(SimpleToken.SEMICOLON)) {
            parser.next();
            PositionedToken positionedToken = parser.previous();
            initializer = new EmptyStatement(positionedToken.pos());
        } else if (parser.hasNext(KeywordToken.VAR) || parser.hasNext(KeywordToken.VAL)) {
            PositionedToken positionedToken = parser.peek();
            initializer = variableStatement(parser, positionedToken.token() == KeywordToken.VAR);
        } else {
            initializer = expressionStatement(parser);
        }
        // Semicolon is handled by the variable or expression statement

        Expression condition;
        if (parser.hasNext(SimpleToken.SEMICOLON)) {
            PositionedToken positionedToken = parser.peek();
            condition = new ValueExpression(Value.BooleanValue.TRUE, positionedToken.pos());
        } else {
            condition = parser.expression();
        }
        parser.expect(SimpleToken.SEMICOLON);

        Statement incrementer;
        if (parser.hasNext(SimpleToken.SEMICOLON)) {
            parser.next();
            PositionedToken positionedToken = parser.previous();
            incrementer = new EmptyStatement(positionedToken.pos());
        } else {
            incrementer = new ExpressionStatement(parser.expression());
        }

        parser.expect(SimpleToken.END_PAREN);
        var to = parser.previous().getTo();
        var body = parse(parser);
        return new ForLoopStatement(initializer, condition, incrementer, body, new SourceSpan(from, to));
    }

    private static Statement forEachLoop(Parser parser) {
        parser.expect(KeywordToken.FOREACH);
        var from = parser.previous().getFrom();
        parser.expect(SimpleToken.BEGIN_PAREN);
        var name = parser.expectWord();
        var variablePos = parser.previous().pos();
        parser.expect(KeywordToken.IN);
        var expression = parser.expression();
        parser.expect(SimpleToken.END_PAREN);
        var to = parser.previous().getTo();
        var body = parse(parser);

        return new ForEachLoopStatement(expression, name.value(), body, new SourceSpan(from, to), variablePos);
    }

    private static Statement breakStatement(Parser parser) {
        parser.expect(KeywordToken.BREAK);
        var from = parser.previous().getFrom();
        parser.expect(SimpleToken.SEMICOLON);
        return new BreakStatement(new SourceSpan(from, parser.previous().getTo()));
    }

    private static Statement continueStatement(Parser parser) {
        parser.expect(KeywordToken.CONTINUE);
        var from = parser.previous().getFrom();
        parser.expect(SimpleToken.SEMICOLON);
        return new ContinueStatement(new SourceSpan(from, parser.previous().getTo()));
    }

    private static Statement importStatement(Parser parser) {
        parser.expect(KeywordToken.IMPORT);
        var from = parser.previous().getFrom();
        var libraryName = parser.expectString().value();
        var namePos = parser.previous().pos();
        if (parser.hasNext(KeywordToken.AS)) {
            parser.next();
            var variableName = parser.expectWord().value();
            namePos = parser.previous().pos();
            parser.expect(SimpleToken.SEMICOLON);
            return new ImportStatement(libraryName, variableName, new SourceSpan(from, parser.previous().getTo()), namePos);
        } else {
            parser.expect(SimpleToken.SEMICOLON);
            return new ImportStatement(libraryName, libraryName, new SourceSpan(from, parser.previous().getTo()), namePos);
        }
    }

    public static Statement parse(Parser parser) {
        var token = parser.peek();
        return switch ((Token) token.token()) {
            case SimpleToken.BEGIN_CURLY -> blockStatement(parser);
            case SimpleToken.SEMICOLON -> {
                parser.next();
                yield new EmptyStatement(token.pos());
            }
            case KeywordToken.APPLY -> applyStatement(parser);
            case KeywordToken.IF -> ifStatement(parser);
            case KeywordToken.VAR -> variableStatement(parser,true);
            case KeywordToken.VAL -> variableStatement(parser,false);
            case KeywordToken.DELETE -> deleteStatement(parser);
            case KeywordToken.RETURN -> returnStatement(parser);
            case KeywordToken.FUNCTION -> functionDeclaration(parser);
            case KeywordToken.WHILE -> whileLoop(parser);
            case KeywordToken.FOR -> forLoop(parser);
            case KeywordToken.FOREACH -> forEachLoop(parser);
            case KeywordToken.BREAK -> breakStatement(parser);
            case KeywordToken.CONTINUE -> continueStatement(parser);
            case KeywordToken.IMPORT -> importStatement(parser);
            default -> expressionStatement(parser);
        };
    }
}
