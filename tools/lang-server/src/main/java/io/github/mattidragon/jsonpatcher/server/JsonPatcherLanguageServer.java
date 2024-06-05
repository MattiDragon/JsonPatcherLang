package io.github.mattidragon.jsonpatcher.server;

import io.github.mattidragon.jsonpatcher.server.document.DocumentManager;
import io.github.mattidragon.jsonpatcher.server.document.SemanticTokenizer;
import io.github.mattidragon.jsonpatcher.server.workspace.WorkspaceManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class JsonPatcherLanguageServer implements LanguageServer, LanguageClientAware {
    private int statusCode = 1;
    private final WorkspaceManager workspaceManager = new WorkspaceManager();
    private final DocumentManager documentService = new DocumentManager(workspaceManager);
    private boolean watchedFilesDynReg;
    private LanguageClient client;

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        documentService.connect(client);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams initializeParams) {
        watchedFilesDynReg = Optional.ofNullable(initializeParams.getCapabilities()
                        .getWorkspace()
                        .getDidChangeWatchedFiles()
                        .getDynamicRegistration())
                .orElse(false);
        
        var workspaceFolders = initializeParams.getWorkspaceFolders();
        if (workspaceFolders != null) {
            workspaceManager.addWorkspaceFolders(workspaceFolders);
        }
        
        var capabilities = new ServerCapabilities();
        
        capabilities.setSemanticTokensProvider(new SemanticTokensWithRegistrationOptions(SemanticTokenizer.LEGEND, true));
        capabilities.setHoverProvider(true);
        
        var syncOptions = new TextDocumentSyncOptions();
        syncOptions.setChange(TextDocumentSyncKind.Full);
        syncOptions.setOpenClose(true);
        capabilities.setTextDocumentSync(syncOptions);
        
        capabilities.setDefinitionProvider(true);
        capabilities.setReferencesProvider(true);
        
        var workspaceCapabilities = new WorkspaceServerCapabilities();
        var folderOptions = new WorkspaceFoldersOptions();
        folderOptions.setSupported(true);
        folderOptions.setChangeNotifications(true);
        workspaceCapabilities.setWorkspaceFolders(folderOptions);
        capabilities.setWorkspace(workspaceCapabilities);
        
        var result = new InitializeResult(capabilities);
        return CompletableFuture.supplyAsync(() -> result);
    }

    @Override
    public void initialized(InitializedParams params) {
        if (watchedFilesDynReg) {
            client.registerCapability(new RegistrationParams(List.of(
                new Registration("permanent:workspace/didChangeWatchedFiles", "workspace/didChangeWatchedFiles", new DidChangeWatchedFilesRegistrationOptions(List.of(
                        new FileSystemWatcher(Either.forLeft("**/*.jsonpatch"))
                )))    
            )));
        }
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        statusCode = 0;
        return CompletableFuture.completedFuture(null);
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
        return workspaceManager;
    }

    @Override
    public void setTrace(SetTraceParams params) {
        // Ignore. We override this to act like we handle this because there isn't a way to turn this off and the default behaviour throws an error.
    }
}
