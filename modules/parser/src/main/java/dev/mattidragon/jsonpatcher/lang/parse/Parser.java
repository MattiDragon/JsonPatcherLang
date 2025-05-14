package dev.mattidragon.jsonpatcher.lang.parse;

import dev.mattidragon.jsonpatcher.lang.ast.Program;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.expression.ErrorExpression;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.ast.statement.Statement;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.metadata.PatchMetadata;
import dev.mattidragon.jsonpatcher.lang.parse.parselet.PostfixParser;
import dev.mattidragon.jsonpatcher.lang.parse.parselet.PrefixParser;
import dev.mattidragon.jsonpatcher.lang.parse.parselet.StatementParser;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.VisibleForTesting;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class Parser {
    private final List<PositionedToken> tokens;
    // We use a list instead of a DiagnosticsBuilder as we sometimes have to roll back previous errors
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final TreeMetadata treeMetadata;
    private final PatchMetadata metadata;
    private int current = 0;

    private Parser(List<PositionedToken> tokens, TreeMetadata treeMetadata) {
        this.tokens = tokens;
        this.metadata = new PatchMetadata();
        this.treeMetadata = treeMetadata;
    }

    public static Result parse(List<PositionedToken> tokens, DiagnosticsBuilder diagnostics) {
        return parse(tokens, diagnostics, new TreeMetadata());
    }

    public static Result parse(List<PositionedToken> tokens, DiagnosticsBuilder diagnostics, TreeMetadata treeMetadata) {
        var parser = new Parser(tokens, treeMetadata);
        var result = parser.program();
        parser.diagnostics.forEach(diagnostics::addDiagnostic);
        return result;
    }

    @VisibleForTesting
    public static Expression parseExpression(List<PositionedToken> tokens, DiagnosticsBuilder diagnostics) throws ParseException {
        var parser = new Parser(tokens, new TreeMetadata());
        Expression expression = null;
        try {
            expression = parser.expression();
        } catch (ParseException e) {
            parser.addError(e.diagnostic());
        }
        parser.diagnostics.forEach(diagnostics::addDiagnostic);
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
                addError(e.diagnostic());
            }
        }
        
        var statements = new ArrayList<Statement>();
        try {
            while (hasNext()) {
                statements.add(statement());
            }
        } catch (ParseException e) {
            addError(e.diagnostic());
        }

        var end = start == null ? null : previous().to();
        var program = new Program(statements);
        if (start != null) {
            treeMetadata.put(program, MetadataKey.FULL_POS, new SourceSpan(start, end));
        }
        return new Result(program, metadata, treeMetadata);
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
            addError(e.diagnostic());
            left = setMetadata(new ErrorExpression(e.diagnostic()), MetadataKey.FULL_POS, e.diagnostic().pos());
        }

        while (hasNext()) {
            try {
                var postfix = PostfixParser.get(this, precedence, left);
                if (postfix == null) break;
                left = postfix;
            } catch (ParseException e) {
                addError(e.diagnostic());
                left = setMetadata(new ErrorExpression(e.diagnostic()), MetadataKey.FULL_POS, e.diagnostic().pos());
            }
        }

        return left;
    }

    public <N extends MetadataHolder, T> N setMetadata(N node, MetadataKey<T> key, T value) {
        treeMetadata.put(node, key, value);
        return node;
    }
    
    public <T> void copyMetadata(MetadataHolder from, MetadataHolder to, MetadataKey<T> key) {
        treeMetadata.get(from, key).ifPresent(value -> treeMetadata.put(to, key, value));
    }
    
    public <T> Optional<T> getMetadata(MetadataHolder from, MetadataKey<T> key) {
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
        return expectFail("word", token);
    }

    public Token.StringToken expectString() {
        var token = next().token();
        if (token instanceof Token.StringToken stringToken) return stringToken;
        return expectFail("string", token);
    }

    public String expectWordOrString() {
        var token = next().token();
        if (token instanceof Token.WordToken(String word)) return word;
        if (token instanceof Token.StringToken(String string)) return string;
        return expectFail("word or string", token);
    }

    public Token.NumberToken expectNumber() {
        var token = next().token();
        if (token instanceof Token.NumberToken numberToken) return numberToken;
        return expectFail("number", token);
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
            addError(new SourceSpan(semicolonPos, semicolonPos), "Expected " + token.explain(), ParseDiagnostic.Code.UNEXPECTED_TOKEN);
        }
    }

    public void expect(Token token) {
        var found = next().token();
        if (found != token) expectFail(token.explain(), found);
    }

    @Contract("_, _ -> fail")
    private <T> T expectFail(String expected, Token actual) {
        throw new ParseException(new ParseDiagnostic(previous().pos(),
                null,
                "Expected %s, but found %s".formatted(expected, actual.explain()),
                ParseDiagnostic.Code.UNEXPECTED_TOKEN));
    }

    public PositionedToken next() {
        if (!hasNext()) {
            addError(new SourceSpan(previous().to(), previous().to()), "Unexpected end of file", ParseDiagnostic.Code.EOF);
            return new PositionedToken(previous().pos().to().offset(1).toSpan(), Token.EofToken.EOF);
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
            addError(new SourceSpan(previous().to(), previous().to()), "Unexpected end of file", ParseDiagnostic.Code.EOF);
            return new PositionedToken(previous().pos().to().offset(1).toSpan(), Token.EofToken.EOF);
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
        return new Position(current, List.copyOf(diagnostics));
    }

    public void loadPos(Position pos) {
        current = pos.current;
        diagnostics.clear();
        diagnostics.addAll(pos.errors);
    }

    public void addError(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
    }

    public void addError(SourceSpan pos, @Nullable ProgramNode node, String message, ParseDiagnostic.Code code) {
        addError(new ParseDiagnostic(pos, node, message, code));
    }

    public void addError(SourceSpan pos, String message, ParseDiagnostic.Code code) {
        addError(pos, null, message, code);
    }

    public static class ParseException extends RuntimeException {
        private final ParseDiagnostic diagnostic;

        public ParseException(ParseDiagnostic diagnostic) {
            super("Parsers error, should not be visible: " + diagnostic.message);
            this.diagnostic = diagnostic;
        }

        public ParseDiagnostic diagnostic() {
            return diagnostic;
        }
    }

    public record ParseDiagnostic(SourceSpan pos, @Nullable ProgramNode node, String message, Code code) implements Diagnostic {
        @Override
        public Kind kind() {
            return Kind.ERROR;
        }

        @Override
        public String id() {
            return code.id;
        }

        public enum Code {
            EOF,
            INVALID_TOKEN,
            ILLEGAL_ASSIGNMENT,
            UNKNOWN_TYPE,
            DUPLICATE_PARAMETER,
            ILLEGAL_VARARGS,
            ILLEGAL_PARAMETER_ORDER,
            UNEXPECTED_TOKEN;

            private final String id = "PARSE-" + ordinal();
        }
    }

    public record Position(int current, List<Diagnostic> errors) {
    }

    public record Result(Program program, PatchMetadata metadata, TreeMetadata treeMetadata) {
    }
}
