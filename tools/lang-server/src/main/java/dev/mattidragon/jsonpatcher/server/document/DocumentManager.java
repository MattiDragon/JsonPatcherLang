package dev.mattidragon.jsonpatcher.server.document;

import dev.mattidragon.jsonpatcher.server.event.GlobalEventBus;
import dev.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DocumentManager implements TextDocumentService, LanguageClientAware {
    private final Map<String, DocumentState> documents = new HashMap<>();
    private final WorkspaceManager workspace;
    private final GlobalEventBus eventBus;
    private @Nullable LanguageClient client;
    private TextDocumentClientCapabilities clientCapabilities = new TextDocumentClientCapabilities();

    public DocumentManager(WorkspaceManager workspace, GlobalEventBus eventBus) {
        this.workspace = workspace;
        this.eventBus = eventBus;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }

    public void setClientCapabilities(TextDocumentClientCapabilities clientCapabilities) {
        this.clientCapabilities = clientCapabilities;
    }

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        if (client == null) {
            throw new IllegalStateException("Client not connected to document service");
        }
        var document = params.getTextDocument();
        var name = document.getUri();

        var state = new DocumentState(name, client, clientCapabilities, workspace, eventBus);
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
        var state = documents.remove(params.getTextDocument().getUri());
        if (state != null) {
            state.close();
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {

    }

    @Override
    public CompletableFuture<@Nullable Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.autoComplete(params.getPosition());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<@Nullable SemanticTokens> semanticTokensFull(SemanticTokensParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.getSemanticTokens();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<@Nullable List<? extends Location>> references(ReferenceParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.getReferences(params.getPosition());
        }
        return CompletableFuture.completedFuture(null);
    }
    
    @Override
    public CompletableFuture<@Nullable Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.getDefinitions(params.getPosition());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<@Nullable Hover> hover(HoverParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.getHover(params.getPosition());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<@Nullable List<InlayHint>> inlayHint(InlayHintParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.getInlayHints(params.getRange());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<@Nullable List<? extends DocumentHighlight>> documentHighlight(DocumentHighlightParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.getHighlight(params.getPosition());
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<@Nullable List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        var state = documents.get(params.getTextDocument().getUri());
        if (state != null) {
            return state.formatDocument(params.getOptions());
        }
        return CompletableFuture.completedFuture(null);
    }
}
