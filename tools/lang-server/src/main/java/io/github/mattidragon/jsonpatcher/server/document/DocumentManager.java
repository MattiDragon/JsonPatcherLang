package io.github.mattidragon.jsonpatcher.server.document;

import io.github.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DocumentManager implements TextDocumentService, LanguageClientAware {
    private final Map<String, DocumentState> documents = new HashMap<>();
    private final WorkspaceManager workspace;
    private LanguageClient client;

    public DocumentManager(WorkspaceManager workspace) {
        this.workspace = workspace;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var document = params.getTextDocument();
        var name = document.getUri();

        var state = new DocumentState(name, client, workspace);
        state.updateContent(document.getText());
        documents.put(name, state);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        var changes = params.getContentChanges();

        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            state.updateContent(changes.getFirst().getText());
        }
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        documents.remove(params.getTextDocument().getUri());
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {

    }

    @Override
    public CompletableFuture<SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.getSemanticTokens();
        }
        return null;
    }

    @Override
    public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.getReferences(params.getPosition());
        }
        return null;
    }
    
    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.getDefinitions(params.getPosition());
        }
        return null; 
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.getHover(params.getPosition());
        }
        return null;
    }
}
