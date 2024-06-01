package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.lang.PositionedException;
import io.github.mattidragon.jsonpatcher.lang.parse.Lexer;
import io.github.mattidragon.jsonpatcher.lang.parse.Parser;
import io.github.mattidragon.jsonpatcher.lang.parse.PositionedToken;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.Program;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DocumentState {
    private final String name;
    private final LanguageClient client;

    private CompletableFuture<Result<List<PositionedToken>>> tokens = CompletableFuture.completedFuture(Result.empty());
    private CompletableFuture<Result<Program>> tree = CompletableFuture.completedFuture(Result.empty());
    
    public DocumentState(String name, LanguageClient client) {
        this.name = name;
        this.client = client;
    }

    public void updateContent(String content) {
        tokens = CompletableFuture.supplyAsync(() -> {
            var lexResult = Lexer.lex(content, name);
            return Result.partial(lexResult.tokens(), Collections.unmodifiableList(lexResult.errors()));
        }, DocumentManager.EXECUTOR);
        tree = tokens.thenApplyAsync(lexResult -> {
            if (lexResult.result.isEmpty()) return Result.fail(lexResult.errors);
            var parseResult = Parser.parse(lexResult.result.get());
            
            var combinedErrors = new ArrayList<PositionedException>(parseResult.errors());
            combinedErrors.addAll(lexResult.errors);
            return Result.partial(parseResult.program(), Collections.unmodifiableList(combinedErrors));
        }, DocumentManager.EXECUTOR);
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
        }, DocumentManager.EXECUTOR);
    }

    public CompletableFuture<SemanticTokens> getSemanticTokens() {
        return tree.thenApplyAsync(result -> result.result.map(SemanticTokenizer::getTokens).orElseGet(SemanticTokens::new), DocumentManager.EXECUTOR);
    }
    
    public static Range spanToRange(SourceSpan span) {
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
