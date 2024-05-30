package io.github.mattidragon.jsonpatcher.server.document;

import org.eclipse.lsp4j.SemanticTokens;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DocumentManager implements LanguageClientAware {
    private final Map<String, DocumentState> documents = new HashMap<>();
    private LanguageClient client;

    public void beginTracking(String name, String contents) {
        var state = new DocumentState(name, client);
        state.updateContent(contents);
        documents.put(name, state);
    }
    
    public void update(String name, String contents) {
        var state = documents.get(name);
        if (state != null) {
            state.updateContent(contents);
        }
    }
    
    public void stopTracking(String name) {
        var state = documents.remove(name);
        if (state != null) {
            state.close();
        }
    }

    public CompletableFuture<SemanticTokens> getSemanticToken(String name) {
        var state = documents.get(name);
        if (state != null) {
            return state.getSemanticTokens();
        }
        return null;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
    }
}
