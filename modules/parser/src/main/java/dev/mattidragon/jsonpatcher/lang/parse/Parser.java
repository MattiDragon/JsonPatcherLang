package dev.mattidragon.jsonpatcher.lang.parse;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.PositionedException;
import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.ErrorExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.Statement;
import dev.mattidragon.jsonpatcher.lang.parse.parselet.PostfixParser;
import dev.mattidragon.jsonpatcher.lang.parse.parselet.PrefixParser;
import dev.mattidragon.jsonpatcher.lang.parse.parselet.StatementParser;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Parser {
    private final LangConfig config;
    private final List<PositionedToken> tokens;
    private final List<ParseException> errors = new ArrayList<>();
    private final TreeMetadata treeMetadata = new TreeMetadata();
    private final PatchMetadata metadata;
    private int current = 0;

    private Parser(LangConfig config, List<PositionedToken> tokens) {
        this.config = config;
        this.tokens = tokens;
        this.metadata = new PatchMetadata();
    }

    public static Result parse(LangConfig config, List<PositionedToken> tokens) {
        return new Parser(config, tokens).program();
    }

    @VisibleForTesting
    public static Expression parseExpression(LangConfig config, List<PositionedToken> tokens) throws ParseException {
        var parser = new Parser(config, tokens);
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
        return Objects.requireNonNull(expression, "Something went wrong, the expression is null without error");
    }

    public Result program() {
        var start = hasNext() ? peek().from() : null;
        while (hasNext(Token.SimpleToken.AT_SIGN)) {
            try {
                next();
                var id = expectWord().value();
                metadata.add(id, this);
                expect(Token.SimpleToken.SEMICOLON);
            } catch (ParseException e) {
                errors.add(e);
            } catch (EndParsingException ignored) {}
        }
        
        var statements = new ArrayList<Statement>();
        try {
            while (hasNext()) {
                statements.add(statement());
            }
        } catch (ParseException e) {
            errors.add(e);
        } catch (EndParsingException ignored) {}

        var end = start == null ? null : previous().to();
        var program = new Program(statements);
        if (start != null) {
            treeMetadata.put(program, MetadataKey.FULL_POS, new SourceSpan(start, end));
        }
        return new Result(program, metadata, treeMetadata, errors);
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

    public <N extends ProgramNode, T> N setMetadata(N node, MetadataKey<T> key, T value) {
        treeMetadata.put(node, key, value);
        return node;
    }
    
    public <T> void copyMetadata(ProgramNode from, ProgramNode to, MetadataKey<T> key) {
        treeMetadata.get(from, key).ifPresent(value -> treeMetadata.put(to, key, value));
    }
    
    public <T> Optional<T> getMetadata(ProgramNode from, MetadataKey<T> key) {
        return treeMetadata.get(from, key);
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
        if (token instanceof Token.WordToken(String word)) return word;
        if (token instanceof Token.StringToken(String string)) return string;
        return expectFail("word or string");
    }

    public Token.NumberToken expectNumber() {
        var token = next().token();
        if (token instanceof Token.NumberToken numberToken) return numberToken;
        return expectFail("number");
    }


    /**
     * Expects a semicolon token and consumes it if present.
     * This method differs from {@link #expect(Token)} in that it doesn't throw the error,
     * instead just adding it to the error list. 
     * This allows for other code to be parsed more correctly afterward.
     * This method also uses special logic for positioning the error where the token should appear instead of at the next token.
     */
    public void expectSoftly(Token token) {
        var semicolonPos = previous().pos().to().offset(1);
        if (hasNext() && peek().token() == token) {
            next();
        } else {
            addError(new ParseException("Expected " + token.explain(), new SourceSpan(semicolonPos, semicolonPos)));
        }
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
            errors.add(new ParseException("Unexpected end of file", new SourceSpan(previous().to(), previous().to())));
            throw new EndParsingException();
        }
        return tokens.get(current++);
    }

    @Contract(pure = true)
    public PositionedToken previous() {
        if (current == 0) throw new IllegalStateException("No previous token (the parser is broken)");
        return tokens.get(current - 1);
    }

    public PositionedToken peek() {
        if (!hasNext()) {
            errors.add(new ParseException("Unexpected end of file", new SourceSpan(previous().to(), previous().to())));
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

    public class ParseException extends PositionedException {
        public final SourceSpan pos;

        public ParseException(String message, SourceSpan pos) {
            super(Parser.this.config, message);
            this.pos = pos;
        }

        @Override
        protected String getBaseMessage() {
            return "Error while parsing patch";
        }

        @Override
        public SourceSpan getPos() {
            return pos;
        }
    }

    public record Position(int current, List<ParseException> errors) {
    }

    public record Result(Program program, PatchMetadata metadata, TreeMetadata treeMetadata, List<ParseException> errors) {
    }
}
