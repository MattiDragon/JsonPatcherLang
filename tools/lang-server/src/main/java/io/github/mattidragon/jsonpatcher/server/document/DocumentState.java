package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.docs.data.DocEntry;
import io.github.mattidragon.jsonpatcher.docs.parse.DocParseException;
import io.github.mattidragon.jsonpatcher.docs.parse.DocParser;
import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.parse.Lexer;
import io.github.mattidragon.jsonpatcher.lang.parse.Parser;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.server.Util;
import io.github.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class DocumentState {
    private final String name;
    private final LanguageClient client;
    private final DefinitionFinder definitionFinder;
    private final LangConfig config;

    private CompletableFuture<TreeAnalysis> analysis = CompletableFuture.failedFuture(new IllegalStateException("Not ready yet"));
    private CompletableFuture<List<DocEntry>> docs = CompletableFuture.failedFuture(new IllegalStateException("Not ready yet"));

    public DocumentState(String name, LanguageClient client, WorkspaceManager workspace, LangConfig config) {
        this.name = name;
        this.client = client;
        this.definitionFinder = new DefinitionFinder(() -> analysis, () -> docs, workspace, name);
        this.config = config;
    }

    public void updateContent(String content) {
        record LexTuple(Lexer.Result result, DocParser docs) {}
        
        var lexResult = CompletableFuture.supplyAsync(() -> {
            var docParser = new DocParser(config);
            var result = Lexer.lex(config, content, name, docParser);
            return new LexTuple(result, docParser);
        }, Util.EXECUTOR);
        
        var tokens = lexResult.thenApply(LexTuple::result).thenApply(Lexer.Result::tokens);
        var lexErrors = lexResult.thenApply(LexTuple::result).thenApply(Lexer.Result::errors);
        
        var docResult = lexResult.thenApply(LexTuple::docs);
        docs = docResult.thenApply(DocParser::getEntries);
        var docErrors = docResult.thenApply(DocParser::getErrors);
        
        var parseResult = tokens.thenApplyAsync(tokens1 -> Parser.parse(config, tokens1), Util.EXECUTOR);
        var tree = parseResult.thenApply(Parser.Result::program);
        var metadata = parseResult.thenApply(Parser.Result::metadata);
        var parseErrors = parseResult.thenApply(Parser.Result::errors);

        analysis = tree.thenApplyAsync(TreeAnalysis::new, Util.EXECUTOR);

        setupDiagnostics(lexErrors, parseErrors, docErrors, analysis);
    }

    private void setupDiagnostics(CompletableFuture<List<Lexer.LexException>> lexErrors, 
                                  CompletableFuture<List<Parser.ParseException>> parseErrors, 
                                  CompletableFuture<List<DocParseException>> docErrors, 
                                  CompletableFuture<TreeAnalysis> analysis) {
        var combinedErrors = Util.combineLists(lexErrors, parseErrors, docErrors);
        
        combinedErrors.thenAcceptBothAsync(analysis, (errors, treeAnalysis) -> {
            var diagnostics = new ArrayList<Diagnostic>();
            
            for (var error : errors) {
                if (error.getPos() == null) continue;
                var diagnostic = new Diagnostic(spanToRange(error.getPos()), error.getInternalMessage());
                diagnostic.setSeverity(DiagnosticSeverity.Error);
                diagnostics.add(diagnostic);
            }
            
            for (var variable : treeAnalysis.getUnresolvedVariables()) {
                var diagnostic = new Diagnostic(spanToRange(variable.pos()), "Cannot find variable '%s'".formatted(variable.name()));
                diagnostic.setSeverity(DiagnosticSeverity.Error);
                diagnostics.add(diagnostic);
            }
            
            for (var variable : treeAnalysis.getUnusedVariables()) {
                var pos = variable.definitionPos();
                if (pos == null) continue;
                var diagnostic = new Diagnostic(spanToRange(pos), "Unused declaration");
                diagnostic.setSeverity(DiagnosticSeverity.Hint);
                diagnostic.setTags(List.of(DiagnosticTag.Unnecessary));
                diagnostics.add(diagnostic);
            }
            
            for (var variable : treeAnalysis.getIllegalMutations()) {
                var diagnostic = new Diagnostic(spanToRange(variable.pos()), "'%s' cannot be reassigned".formatted(variable.name()));
                diagnostic.setSeverity(DiagnosticSeverity.Error);
                diagnostics.add(diagnostic);
            }
            
            for (var variable : treeAnalysis.getRedefinitions()) {
                var pos = variable.definitionPos();
                if (pos == null) continue;
                Diagnostic diagnostic;
                if (variable instanceof TreeAnalysis.ParameterDefinition) {
                    diagnostic = new Diagnostic(spanToRange(pos), "Parameter '%s' shadows pre-exising variable".formatted(variable.name()));
                    diagnostic.setSeverity(DiagnosticSeverity.Hint);
                } else {
                    diagnostic = new Diagnostic(spanToRange(pos), "A variable by the name '%s' is already defined in this scope".formatted(variable.name()));
                    diagnostic.setSeverity(DiagnosticSeverity.Error);
                }
                diagnostics.add(diagnostic);
            }
            
            diagnostics.forEach(diagnostic -> diagnostic.setSource("JsonPatcher"));
            client.publishDiagnostics(new PublishDiagnosticsParams(name, diagnostics));
        }, Util.EXECUTOR);
    }

    public CompletableFuture<SemanticTokens> getSemanticTokens() {
        return analysis.thenCombineAsync(docs, SemanticTokenizer::getTokens, Util.EXECUTOR);
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
