package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.docs.parse.DocParseException;
import io.github.mattidragon.jsonpatcher.docs.parse.DocParser;
import io.github.mattidragon.jsonpatcher.lang.parse.Lexer;
import io.github.mattidragon.jsonpatcher.lang.parse.Parser;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DocumentState {
    private final String name;
    private final LanguageClient client;
    
    private CompletableFuture<TreeAnalysis> analysis = CompletableFuture.failedFuture(new IllegalStateException("Not ready yet"));
    
    public DocumentState(String name, LanguageClient client) {
        this.name = name;
        this.client = client;
    }

    public void updateContent(String content) {
        record LexPair(Lexer.Result result, DocParser docs) {}
        
        var lexResult = CompletableFuture.supplyAsync(() -> {
            var docParser = new DocParser();
            var result = Lexer.lex(content, name, docParser);
            return new LexPair(result, docParser);
        }, DocumentManager.EXECUTOR);
        
        var tokens = lexResult.thenApply(LexPair::result).thenApply(Lexer.Result::tokens);
        var lexErrors = lexResult.thenApply(LexPair::result).thenApply(Lexer.Result::errors);
        
        var docResult = lexResult.thenApply(LexPair::docs);
        var docs = docResult.thenApply(DocParser::getEntries);
        var docErrors = docResult.thenApply(DocParser::getErrors);
        
        var parseResult = tokens.thenApplyAsync(Parser::parse, DocumentManager.EXECUTOR);
        var tree = parseResult.thenApply(Parser.Result::program);
        var metadata = parseResult.thenApply(Parser.Result::metadata);
        var parseErrors = parseResult.thenApply(Parser.Result::errors);

        analysis = tree.thenApplyAsync(TreeAnalysis::new, DocumentManager.EXECUTOR);

        setupDiagnostics(lexErrors, parseErrors, docErrors, analysis);
    }

    private void setupDiagnostics(CompletableFuture<List<Lexer.LexException>> lexErrors, 
                                  CompletableFuture<List<Parser.ParseException>> parseErrors, 
                                  CompletableFuture<List<DocParseException>> docErrors, 
                                  CompletableFuture<TreeAnalysis> analysis) {
        var combinedErrors = combineLists(lexErrors, parseErrors, docErrors);
        
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
        }, DocumentManager.EXECUTOR);
    }

    @SafeVarargs
    private <T> CompletableFuture<List<T>> combineLists(CompletableFuture<? extends List<? extends T>>... futures) {
        var marker = CompletableFuture.allOf(futures);
        return marker.thenApply(unit -> {
            var list = new ArrayList<T>();
            for (var future : futures) {
                list.addAll(future.join());
            }
            return list;
        });
    } 
    
    public CompletableFuture<SemanticTokens> getSemanticTokens() {
        return analysis.thenApplyAsync(SemanticTokenizer::getTokens, DocumentManager.EXECUTOR);
    }
    
    public static Range spanToRange(SourceSpan span) {
        var pos1 = new Position(span.from().row() - 1, span.from().column() - 1);
        var pos2 = new Position(span.to().row() - 1, span.to().column());
        return new Range(pos1, pos2);
    }
}
