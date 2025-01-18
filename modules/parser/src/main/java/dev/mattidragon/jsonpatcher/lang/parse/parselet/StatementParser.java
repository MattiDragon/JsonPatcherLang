package dev.mattidragon.jsonpatcher.lang.parse.parselet;

import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.BooleanExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.statement.*;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;

import java.util.ArrayList;
import java.util.Optional;

import static dev.mattidragon.jsonpatcher.lang.parse.Token.KeywordToken;
import static dev.mattidragon.jsonpatcher.lang.parse.Token.SimpleToken;

public class StatementParser {
    private StatementParser() {
    }

    static Statement blockStatement(Parser parser) {
        parser.expect(SimpleToken.BEGIN_CURLY);
        var beginPos = parser.previous().from();
        var statements = new ArrayList<Statement>();
        try {
            while (parser.peek().token() != SimpleToken.END_CURLY) {
                statements.add(parse(parser));
            }
        } catch (Parser.ParseException e) {
            parser.addError(e.diagnostic());
            parser.seek(SimpleToken.END_CURLY);
            return parser.setMetadata(new ErrorStatement(e.diagnostic()), MetadataKey.FULL_POS, e.diagnostic().pos());
        }
        parser.expect(SimpleToken.END_CURLY);
        var endPos = parser.previous().to();
        return parser.setMetadata(new BlockStatement(statements), MetadataKey.FULL_POS, new SourceSpan(beginPos, endPos));
    }

    private static Statement applyStatement(Parser parser) {
        parser.expect(KeywordToken.APPLY);
        var keywordPos = parser.previous().pos();
        
        parser.expect(SimpleToken.BEGIN_PAREN);
        var root = parser.expression();
        parser.expect(SimpleToken.END_PAREN);
        var action = parse(parser);
        var endPos = parser.previous().to();
        
        var statement = new ApplyStatement(root, action);
        parser.setMetadata(statement, MetadataKey.KEYWORD_POS, keywordPos);
        parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(keywordPos.from(), endPos));
        return statement;
    }

    private static Statement ifStatement(Parser parser) {
        parser.expect(KeywordToken.IF);
        var keywordPos = parser.previous().pos();
        parser.expect(SimpleToken.BEGIN_PAREN);
        var condition = parser.expression();
        parser.expect(SimpleToken.END_PAREN);
        var action = parse(parser);
        Statement elseAction = null;
        if (parser.hasNext(KeywordToken.ELSE)) {
            parser.next();
            elseAction = parse(parser);
        }
        var endPos = parser.previous().to();
        
        var statement = new IfStatement(condition, action, elseAction);
        parser.setMetadata(statement, MetadataKey.KEYWORD_POS, keywordPos);
        parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(keywordPos.from(), endPos));
        return statement;
    }

    private static Statement variableStatement(Parser parser, boolean mutable) {
        var keywordPos = parser.next().pos();
        var name = parser.expectWord().value();
        var namePos = parser.previous().pos();
        parser.expect(SimpleToken.ASSIGN);
        var initializer = parser.expression();
        parser.expectSoftly(SimpleToken.SEMICOLON);
        var endPos = parser.previous().to();

        var statement = new VariableCreationStatement(name, initializer, mutable);
        parser.setMetadata(statement, MetadataKey.KEYWORD_POS, keywordPos);
        parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(keywordPos.from(), endPos));
        parser.setMetadata(statement, MetadataKey.NAME_POS, namePos);
        parser.setMetadata(statement, MetadataKey.MAIN_POS, namePos);
        return statement;
    }

    private static Statement deleteStatement(Parser parser) {
        var keywordPos = parser.next().pos();
        var expression = parser.expression();
        var ref = PostfixParser.checkReference(parser, expression, keywordPos);
        parser.expectSoftly(SimpleToken.SEMICOLON);
        var endPos = parser.previous().to();

        var statement = new DeleteStatement(ref);
        parser.setMetadata(statement, MetadataKey.KEYWORD_POS, keywordPos);
        parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(keywordPos.from(), endPos));
        return statement;
    }

    private static Statement returnStatement(Parser parser) {
        var keywordPos = parser.next().pos();
        
        var positionedToken = parser.peek();
        Optional<Expression> value = positionedToken.token() == SimpleToken.SEMICOLON 
                ? Optional.empty() 
                : Optional.of(parser.expression());
        
        parser.expectSoftly(SimpleToken.SEMICOLON);
        var endPos = parser.previous().to();
        
        var statement = new ReturnStatement(value);
        parser.setMetadata(statement, MetadataKey.KEYWORD_POS, keywordPos);
        parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(keywordPos.from(), endPos));
        return statement;
    }

    private static FunctionDeclarationStatement functionDeclaration(Parser parser) {
        parser.expect(KeywordToken.FUNCTION);
        var keywordPos = parser.previous().pos();
        
        var name = parser.expectWord().value();
        var namePos = parser.previous().pos();

        parser.expect(SimpleToken.BEGIN_PAREN);
        var expressionStartPos = parser.previous().from();
        var arguments = PrefixParser.parseArgumentList(parser);
        var body = blockStatement(parser);
        var endPos = parser.previous().to();
        
        var expression = new FunctionExpression(body, arguments);
        parser.setMetadata(expression, MetadataKey.FULL_POS, new SourceSpan(expressionStartPos, endPos));
        var statement = new FunctionDeclarationStatement(name, expression);
        parser.setMetadata(statement, MetadataKey.KEYWORD_POS, keywordPos);
        parser.setMetadata(statement, MetadataKey.NAME_POS, namePos);
        parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(keywordPos.from(), endPos));
        return statement;
    }

    private static Statement expressionStatement(Parser parser) {
        Expression expression;
        try {
            expression = parser.expression();
        } catch (Parser.ParseException e) {
            parser.addError(e.diagnostic());
            parser.seek(SimpleToken.SEMICOLON);
            return parser.setMetadata(new ErrorStatement(e.diagnostic()), MetadataKey.FULL_POS, e.diagnostic().pos());
        }

        parser.expectSoftly(SimpleToken.SEMICOLON);

        var statement = new ExpressionStatement(expression);
        parser.copyMetadata(expression, statement, MetadataKey.FULL_POS);
        parser.copyMetadata(expression, statement, MetadataKey.MAIN_POS);
        return statement;
    }

    private static Statement whileLoop(Parser parser) {
        parser.expect(KeywordToken.WHILE);
        var keywordPos = parser.previous().pos();
        parser.expect(SimpleToken.BEGIN_PAREN);
        var condition = parser.expression();
        parser.expect(SimpleToken.END_PAREN);
        var endPos = parser.previous().to();
        var body = parse(parser);

        var statement = new WhileLoopStatement(condition, body);
        parser.setMetadata(statement, MetadataKey.KEYWORD_POS, keywordPos);
        parser.setMetadata(statement, MetadataKey.MAIN_POS, new SourceSpan(keywordPos.from(), endPos));
        parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(keywordPos.from(), parser.previous().to()));
        return statement;
    }

    private static Statement forLoop(Parser parser) {
        parser.expect(KeywordToken.FOR);
        var keywordPos = parser.previous().pos();
        parser.expect(SimpleToken.BEGIN_PAREN);

        Statement initializer;
        if (parser.hasNext(SimpleToken.SEMICOLON)) {
            parser.next();
            initializer = parser.setMetadata(new EmptyStatement(), MetadataKey.FULL_POS, parser.previous().pos());
        } else if (parser.hasNext(KeywordToken.VAR) || parser.hasNext(KeywordToken.VAL)) {
            initializer = variableStatement(parser, parser.peek().token() == KeywordToken.VAR);
        } else {
            initializer = expressionStatement(parser);
        }
        // Semicolon is handled by the variable or expression statement

        Expression condition;
        if (parser.hasNext(SimpleToken.SEMICOLON)) {
            condition = parser.setMetadata(new BooleanExpression(true), MetadataKey.FULL_POS, parser.peek().pos());
        } else {
            condition = parser.expression();
        }
        parser.expectSoftly(SimpleToken.SEMICOLON);

        Statement incrementer;
        if (parser.hasNext(SimpleToken.SEMICOLON)) {
            parser.next();
            incrementer = parser.setMetadata(new EmptyStatement(), MetadataKey.FULL_POS, parser.previous().pos());
        } else {
            var expression = parser.expression();
            incrementer = new ExpressionStatement(expression);
            parser.setMetadata(incrementer, MetadataKey.FULL_POS, parser.getMetadata(expression, MetadataKey.FULL_POS).orElseThrow());
        }

        parser.expect(SimpleToken.END_PAREN);
        var endPos = parser.previous().to();
        var body = parse(parser);
        var statement = new ForLoopStatement(initializer, condition, incrementer, body);
        parser.setMetadata(statement, MetadataKey.KEYWORD_POS, keywordPos);
        parser.setMetadata(statement, MetadataKey.MAIN_POS, new SourceSpan(keywordPos.from(), endPos));
        parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(keywordPos.from(), parser.previous().to()));
        return statement;
    }

    private static Statement forEachLoop(Parser parser) {
        parser.expect(KeywordToken.FOREACH);
        var keywordPos = parser.previous().pos();
        parser.expect(SimpleToken.BEGIN_PAREN);
        var name = parser.expectWord();
        var variablePos = parser.previous().pos();
        parser.expect(KeywordToken.IN);
        var inPos = parser.previous().pos();
        var expression = parser.expression();
        parser.expect(SimpleToken.END_PAREN);
        var endPos = parser.previous().to();
        var body = parse(parser);

        var statement = new ForEachLoopStatement(expression, name.value(), body);
        parser.setMetadata(statement, MetadataKey.KEYWORD_POS, keywordPos);
        parser.setMetadata(statement, MetadataKey.SECONDARY_KEYWORD_POS, inPos);
        parser.setMetadata(statement, MetadataKey.NAME_POS, variablePos);
        parser.setMetadata(statement, MetadataKey.MAIN_POS, new SourceSpan(keywordPos.from(), endPos));
        parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(keywordPos.from(), parser.previous().to()));
        return statement;
    }

    private static Statement breakStatement(Parser parser) {
        parser.expect(KeywordToken.BREAK);
        var tokenPos = parser.previous().pos();
        parser.expectSoftly(SimpleToken.SEMICOLON);
        var statement = new BreakStatement();
        parser.setMetadata(statement, MetadataKey.KEYWORD_POS, tokenPos);
        parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(tokenPos.from(), parser.previous().to()));
        return statement;
    }

    private static Statement continueStatement(Parser parser) {
        parser.expect(KeywordToken.CONTINUE);
        var tokenPos = parser.previous().pos();
        parser.expectSoftly(SimpleToken.SEMICOLON);
        var statement = new ContinueStatement();
        parser.setMetadata(statement, MetadataKey.KEYWORD_POS, tokenPos);
        parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(tokenPos.from(), parser.previous().to()));
        return statement;
    }

    private static Statement importStatement(Parser parser) {
        parser.expect(KeywordToken.IMPORT);
        var keywordPos = parser.previous().pos();
        var libraryName = parser.expectString().value();
        var namePos = parser.previous().pos();
        if (parser.hasNext(KeywordToken.AS)) {
            var asPos = parser.next().pos();
            var variableName = parser.expectWord().value();
            var varPos = parser.previous().pos();
            parser.expectSoftly(SimpleToken.SEMICOLON);
            var statement = new ImportStatement(libraryName, variableName);
            parser.setMetadata(statement, MetadataKey.KEYWORD_POS, keywordPos);
            parser.setMetadata(statement, MetadataKey.SECONDARY_KEYWORD_POS, asPos);
            parser.setMetadata(statement, MetadataKey.NAME_POS, varPos);
            parser.setMetadata(statement, MetadataKey.IMPORT_LOCATION_POS, namePos);
            parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(keywordPos.from(), parser.previous().to()));
            return statement;
        } else {
            parser.expectSoftly(SimpleToken.SEMICOLON);
            var statement = new ImportStatement(libraryName, libraryName);
            parser.setMetadata(statement, MetadataKey.KEYWORD_POS, keywordPos);
            parser.setMetadata(statement, MetadataKey.NAME_POS, namePos);
            parser.setMetadata(statement, MetadataKey.IMPORT_LOCATION_POS, namePos);
            parser.setMetadata(statement, MetadataKey.FULL_POS, new SourceSpan(keywordPos.from(), parser.previous().to()));
            return statement;
        }
    }

    public static Statement parse(Parser parser) {
        var token = parser.peek();
        return switch (token.token()) {
            case SimpleToken.BEGIN_CURLY -> blockStatement(parser);
            case SimpleToken.SEMICOLON -> {
                parser.next();
                yield new EmptyStatement();
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
