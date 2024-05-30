package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.lang.PositionedException;
import io.github.mattidragon.jsonpatcher.lang.parse.Lexer;
import io.github.mattidragon.jsonpatcher.lang.parse.Parser;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.Program;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DocumentState implements AutoCloseable {
    private final String name;
    private final LanguageClient client;

    private boolean closed = false;
    private CompletableFuture<Result<Lexer.Result>> tokens = CompletableFuture.completedFuture(Result.empty());
    private CompletableFuture<Result<Program>> tree = CompletableFuture.completedFuture(Result.empty());
    
    public DocumentState(String name, LanguageClient client) {
        this.name = name;
        this.client = client;
    }

    public void updateContent(String content) {
        tokens = CompletableFuture.supplyAsync(() -> {
            Lexer.Result lexResult;
            try {
                lexResult = Lexer.lex(content, name);
            } catch (Lexer.LexException e) {
                return Result.fail(List.of(e));
            }
            return Result.success(lexResult);
        });
        tree = tokens.thenApply(lexResult -> {
            if (lexResult.result.isEmpty()) return Result.fail(lexResult.errors);
            var parseResult = Parser.parse(lexResult.result.get().tokens());
            return switch (parseResult) {
                case Parser.Result.Success(var program, var metadata) -> 
                        Result.success(program);
                case Parser.Result.Fail(var program, var metadata, var errors) ->
                        Result.partial(program, Collections.unmodifiableList(errors));
            };
        });
        tree.thenAcceptAsync(result -> {
            var errors = result.errors();
            var diagnostics = new ArrayList<Diagnostic>();

            for (var error : errors) {
                var pos = error.getPos();
                if (pos == null) continue;
                var diagnostic = new Diagnostic(spanToRange(pos), error.getInternalMessage());
                diagnostic.setSeverity(DiagnosticSeverity.Error);
                diagnostic.setSource("JsonPatcher");
                diagnostics.add(diagnostic);
            }

            client.publishDiagnostics(new PublishDiagnosticsParams(name, diagnostics));
        });
    }

    @Override
    public void close() {
        closed = true;
    }

    public CompletableFuture<SemanticTokens> getSemanticTokens() {
        return tree.thenApply(result -> result.result.map(SemanticTokenizer::getTokens).orElseGet(SemanticTokens::new));
    }
    
    private static Range spanToRange(SourceSpan span) {
        var pos1 = new Position(span.from().row() - 1, span.from().column() - 1);
        var pos2 = new Position(span.to().row() - 1, span.to().column());
        return new Range(pos1, pos2);
    }
    
    private record Result<T>(Optional<T> result, List<PositionedException> errors) {
        public static <T> Result<T> empty() {
            return new Result<>(Optional.empty(), List.of());
        }

        public static <T> Result<T> success(T value) {
            return new Result<>(Optional.of(value), List.of());
        }

        public static <T> Result<T> partial(T value, List<PositionedException> errors) {
            return new Result<>(Optional.of(value), errors);
        }

        public static <T> Result<T> fail(List<PositionedException> errors) {
            return new Result<>(Optional.empty(), errors);
        }
    }
}
