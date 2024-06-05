package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.docs.data.DocEntry;
import io.github.mattidragon.jsonpatcher.docs.parse.DocParseException;
import io.github.mattidragon.jsonpatcher.docs.parse.DocParser;
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
import java.util.concurrent.CompletableFuture;

public class DocumentState {
    private final String name;
    private final LanguageClient client;
    private final WorkspaceManager workspace;
    private final DefinitionFinder definitionFinder;
    
    private CompletableFuture<TreeAnalysis> analysis = CompletableFuture.failedFuture(new IllegalStateException("Not ready yet"));
    private CompletableFuture<List<DocEntry>> docs = CompletableFuture.failedFuture(new IllegalStateException("Not ready yet"));

    public DocumentState(String name, LanguageClient client, WorkspaceManager workspace) {
        this.name = name;
        this.client = client;
        this.workspace = workspace;
        this.definitionFinder = new DefinitionFinder(() -> analysis, () -> docs, workspace, name);
    }

    public void updateContent(String content) {
        record LexTuple(Lexer.Result result, DocParser docs) {}
        
        var lexResult = CompletableFuture.supplyAsync(() -> {
            var docParser = new DocParser();
            var result = Lexer.lex(content, name, docParser);
            return new LexTuple(result, docParser);
        }, Util.EXECUTOR);
        
        var tokens = lexResult.thenApply(LexTuple::result).thenApply(Lexer.Result::tokens);
        var lexErrors = lexResult.thenApply(LexTuple::result).thenApply(Lexer.Result::errors);
        
        var docResult = lexResult.thenApply(LexTuple::docs);
        docs = docResult.thenApply(DocParser::getEntries);
        var docErrors = docResult.thenApply(DocParser::getErrors);
        
        var parseResult = tokens.thenApplyAsync(Parser::parse, Util.EXECUTOR);
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
                diagnostic.setSource("JsonPatcher");
                diagnostics.add(diagnostic);
            }
            
            for (var variable : treeAnalysis.getUnboundVariables()) {
                var diagnostic = new Diagnostic(spanToRange(variable.pos()), "Cannot find variable '%s'".formatted(variable.name()));
                diagnostic.setSeverity(DiagnosticSeverity.Error);
                diagnostic.setSource("JsonPatcher");
                diagnostics.add(diagnostic);
            }
            
            for (var variable : treeAnalysis.getUnusedVariables()) {
                if (variable.definitionPos() == null) continue;
                var diagnostic = new Diagnostic(spanToRange(variable.definitionPos()), "Unused declaration");
                diagnostic.setSeverity(DiagnosticSeverity.Hint);
                diagnostic.setTags(List.of(DiagnosticTag.Unnecessary));
                diagnostics.add(diagnostic);
            }
            
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
}
