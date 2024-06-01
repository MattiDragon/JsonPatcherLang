package io.github.mattidragon.jsonpatcher.lang.parse;

import io.github.mattidragon.jsonpatcher.lang.PositionedException;
import io.github.mattidragon.jsonpatcher.lang.parse.parselet.PostfixParser;
import io.github.mattidragon.jsonpatcher.lang.parse.parselet.Precedence;
import io.github.mattidragon.jsonpatcher.lang.parse.parselet.PrefixParser;
import io.github.mattidragon.jsonpatcher.lang.parse.parselet.StatementParser;
import io.github.mattidragon.jsonpatcher.lang.runtime.Program;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.ErrorExpression;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.Expression;
import io.github.mattidragon.jsonpatcher.lang.runtime.statement.Statement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;

public class Parser {
    private final List<PositionedToken> tokens;
    private final List<ParseException> errors = new ArrayList<>();
    private final PatchMetadata metadata;
    private int current = 0;

    private Parser(List<PositionedToken> tokens) {
        this.tokens = tokens;
        this.metadata = new PatchMetadata();
    }

    public static Result parse(List<PositionedToken> tokens) {
        return new Parser(tokens).program();
    }

    @VisibleForTesting
    public static Expression parseExpression(List<PositionedToken> tokens) throws ParseException {
        var parser = new Parser(tokens);
        var errors = parser.errors;
        Expression expression = null;
        try {
            expression = parser.expression();
        } catch (EndParsingException ignored) {
        } catch (ParseException e) {
            errors.add(e);
        }
        if (!errors.isEmpty()) {
            var error = new RuntimeException("Expected successful parse");
            errors.forEach(error::addSuppressed);
            throw error;
        }
        return expression;
    }

    public Result program() {
        while (hasNext(Token.SimpleToken.AT_SIGN)) {
            try {
                next();
                var id = expectWord().value();
                metadata.add(id, this);
                expect(Token.SimpleToken.SEMICOLON);
            } catch (ParseException e) {
                errors.add(e);
            }
        }
        
        var statements = new ArrayList<Statement>();
        try {
            while (hasNext()) {
                statements.add(statement());
            }
        } catch (ParseException e) {
            errors.add(e);
        } catch (EndParsingException ignored) {}

        return new Result(new Program(statements), metadata, errors);
    }

    private Statement statement() {
        return StatementParser.parse(this);
    }

    public Expression expression() {
        return expression(Precedence.ROOT);
    }

    public Expression expression(Precedence precedence) {
        Expression left;
        try {
            left = PrefixParser.parse(this, next());
        } catch (ParseException e) {
            errors.add(e);
            left = new ErrorExpression(e);
        }

        while (hasNext()) {
            try {
                var postfix = PostfixParser.get(this, precedence, left);
                if (postfix == null) break;
                left = postfix;
            } catch (ParseException e) {
                errors.add(e);
                left = new ErrorExpression(e);
            }
        }

        return left;
    }

    public void seek(Token token) {
        while (hasNext() && peek().token() != token) {
            next();
        }
        expect(token);
    }

    public Token.WordToken expectWord() {
        var token = next().token();
        if (token instanceof Token.WordToken wordToken) return wordToken;
        return expectFail("word");
    }

    public Token.StringToken expectString() {
        var token = next().token();
        if (token instanceof Token.StringToken stringToken) return stringToken;
        return expectFail("string");
    }

    public String expectWordOrString() {
        var token = next().token();
        if (token instanceof Token.WordToken wordToken) return wordToken.value();
        if (token instanceof Token.StringToken stringToken) return stringToken.value();
        return expectFail("word or string");
    }

    public Token.NumberToken expectNumber() {
        var token = next().token();
        if (token instanceof Token.NumberToken numberToken) return numberToken;
        return expectFail("number");
    }

    public void expect(Token token) {
        var found = next().token();
        if (found != token) expectFail(token.explain());
    }

    @Contract("_ -> fail")
    private  <T> T expectFail(String expected) {
        throw new ParseException("Expected %s, but found %s".formatted(expected, previous().token().explain()), previous().pos());
    }

    public void addError(ParseException error) {
        errors.add(error);
    }

    public PositionedToken next() {
        if (!hasNext()) {
            errors.add(new ParseException("Unexpected end of file", new SourceSpan(previous().getTo(), previous().getTo())));
            throw new EndParsingException();
        }
        return tokens.get(current++);
    }

    public PositionedToken previous() {
        if (current == 0) throw new IllegalStateException("No previous token (the parser is broken)");
        return tokens.get(current - 1);
    }

    public PositionedToken peek() {
        if (!hasNext()) {
            errors.add(new ParseException("Unexpected end of file", new SourceSpan(previous().getTo(), previous().getTo())));
            throw new EndParsingException();
        }
        return tokens.get(current);
    }

    public boolean hasNext() {
        return current < tokens.size();
    }

    public boolean hasNext(Token token) {
        if (!hasNext()) return false;
        PositionedToken positionedToken = peek();
        return positionedToken.token() == token;
    }

    public Position savePos() {
        return new Position(current, List.copyOf(errors));
    }

    public void loadPos(Position pos) {
        current = pos.current;
        errors.clear();
        errors.addAll(pos.errors);
    }

    /**
     * Special error to throw when we reach an error condition from which recovery doesn't make sense (end of file)
     */
    private static class EndParsingException extends RuntimeException {
    }

    public static class ParseException extends PositionedException {
        public final SourceSpan pos;

        public ParseException(String message, SourceSpan pos) {
            super(message);
            this.pos = pos;
        }

        @Override
        protected String getBaseMessage() {
            return "Error while parsing patch";
        }

        @Override
        @Nullable
        public SourceSpan getPos() {
            return pos;
        }
    }

    public record Position(int current, List<ParseException> errors) {
    }

    public record Result(Program program, PatchMetadata metadata, List<ParseException> errors) {
    }
}
