package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.docs.parse.DocParser;
import dev.mattidragon.jsonpatcher.lang.analysis.variable.VariableAnalyser;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostics;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.parse.Parser;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.workspace.DocHolder;
import dev.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

public class DocumentState {
    private final String name;
    private final LanguageClient client;
    private final DefinitionFinder definitionFinder;
    private final AutoCompleteHelper autoCompleteHelper;
    private final Supplier<Map<String, DocHolder.GlobalData>> globalsGetter;
    private final DocHolder docHolder;

    private CompletableFuture<DocumentData> data = CompletableFuture.failedFuture(new IllegalStateException("Not ready yet"));

    public DocumentState(String name, LanguageClient client, WorkspaceManager workspace) {
        this.name = name;
        this.client = client;
        docHolder = workspace.getDocManager().getHolder();
        this.definitionFinder = new DefinitionFinder(() -> data, workspace, name);
        autoCompleteHelper = new AutoCompleteHelper(docHolder, () -> data);
        this.globalsGetter = docHolder::getGlobals;
    }

    public void updateContent(String content) {
        data = CompletableFuture.supplyAsync(() -> {
            var diagnostics = new DiagnosticsBuilder();
            var docParser = new DocParser(diagnostics);
            var tokens = Lexer.lex(content, name, diagnostics, docParser).tokens();

            var tokenLookup = new TokenLookup(tokens);

            var parseResult = Parser.parse(tokens, diagnostics);
            var program = parseResult.program();
            var treeMetadata = parseResult.treeMetadata();
            var metadata = parseResult.metadata();

            var globals = globalsGetter.get()
                    .entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().entry().requiredMetadata().stream().allMatch(metadata::has))
                    .map(Map.Entry::getKey)
                    .toList();
            VariableAnalyser.analyse(program, treeMetadata, diagnostics, globals);
            var lookups = Lookups.get(program, treeMetadata);

            Util.EXECUTOR.submit(() -> sendDiagnostics(diagnostics.build()));
            return new DocumentData(program, treeMetadata, docParser.getEntries(), lookups, tokenLookup);
        }, Util.EXECUTOR);
    }

    private void sendDiagnostics(Diagnostics diagnostics) {
        var lspDiagnostics = new ArrayList<Diagnostic>();

        for (var diagnostic : diagnostics.all()) {
            var pos = diagnostic.pos();
            if (pos == null) continue; // Potentially report file wide problems later
            var lspDiagnostic = new Diagnostic(spanToRange(pos), diagnostic.message());
            lspDiagnostic.setCode(diagnostic.id());
            switch (diagnostic.kind()) {
                case INTERNAL_ERROR, ERROR -> lspDiagnostic.setSeverity(DiagnosticSeverity.Error);
                case WARNING -> lspDiagnostic.setSeverity(DiagnosticSeverity.Warning);
                case UNUSED -> {
                    lspDiagnostic.setSeverity(DiagnosticSeverity.Warning);
                    lspDiagnostic.setTags(List.of(DiagnosticTag.Unnecessary));
                }
            }
            lspDiagnostic.setSource("JsonPatcher");

            lspDiagnostics.add(lspDiagnostic);
        }

        client.publishDiagnostics(new PublishDiagnosticsParams(name, lspDiagnostics));
    }

    public CompletableFuture<SemanticTokens> getSemanticTokens() {
        return data.thenApplyAsync(documentData -> SemanticTokenizer.getTokens(documentData, docHolder), Util.EXECUTOR);
    }

    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> getDefinitions(Position position) {
        return definitionFinder.getDefinitions(position).thenApply(Either::forLeft);
    }

    public CompletableFuture<List<? extends Location>> getReferences(Position position) {
        return definitionFinder.getReferences(position);
    }

    public CompletableFuture<Hover> getHover(Position position) {
        return definitionFinder.getHover(position);
    }

    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> autoComplete(Position position) {
        return autoCompleteHelper.autoComplete(position).thenApply(Either::forRight);
    }

    public static Range spanToRange(SourceSpan span) {
        var pos1 = new Position(span.from().row() - 1, span.from().column() - 1);
        var pos2 = new Position(span.to().row() - 1, span.to().column());
        return new Range(pos1, pos2);
    }

    public static Location spanToLocation(SourceSpan span) {
        if (!Objects.equals(span.from().file().name(), span.to().file().name())) {
            throw new IllegalArgumentException("Cross file span can't be converted to location");
        }
        return new Location(span.from().file().name(), spanToRange(span));
    }
}
