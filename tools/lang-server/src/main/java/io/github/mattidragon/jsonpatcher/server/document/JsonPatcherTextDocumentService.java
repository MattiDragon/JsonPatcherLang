package io.github.mattidragon.jsonpatcher.server.document;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.concurrent.CompletableFuture;

public class JsonPatcherTextDocumentService implements TextDocumentService, LanguageClientAware {
    private final DocumentManager manager = new DocumentManager();

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var document = params.getTextDocument();
        manager.beginTracking(document.getUri(), document.getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var changes = params.getContentChanges();
        manager.update(params.getTextDocument().getUri(), changes.getFirst().getText());
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        manager.stopTracking(params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {

    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        return manager.getSemanticToken(params.getTextDocument().getUri());
//        return null;
    }

    @Override
    public void connect(LanguageClient client) {
        manager.connect(client);
    }
}
