package io.github.mattidragon.jsonpatcher.server;

import io.github.mattidragon.jsonpatcher.server.document.DocumentManager;
import io.github.mattidragon.jsonpatcher.server.document.SemanticTokenizer;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.*;

import java.util.concurrent.CompletableFuture;

public class JsonPatcherLanguageServer implements LanguageServer, LanguageClientAware {
    private int statusCode = 1;
    private final DocumentManager documentService = new DocumentManager();

    @Override
    public void connect(LanguageClient client) {
        documentService.connect(client);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams initializeParams) {
        var capabilities = new ServerCapabilities();
        
        capabilities.setSemanticTokensProvider(new SemanticTokensWithRegistrationOptions(SemanticTokenizer.LEGEND, true));
        
        var syncOptions = new TextDocumentSyncOptions();
        syncOptions.setChange(TextDocumentSyncKind.Full);
        syncOptions.setOpenClose(true);
        capabilities.setTextDocumentSync(syncOptions);
        
        capabilities.setDefinitionProvider(true);
        capabilities.setReferencesProvider(true);
        
        var result = new InitializeResult(capabilities);
        return CompletableFuture.supplyAsync(() -> result);
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        statusCode = 0;
        return null;
    }

    @Override
    public void exit() {
        System.exit(statusCode);
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return documentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new WorkspaceService() {
            @Override
            public void didChangeConfiguration(DidChangeConfigurationParams params) {
                
            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {

            }
        };
    }
}
