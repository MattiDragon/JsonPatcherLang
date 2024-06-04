package io.github.mattidragon.jsonpatcher.server.workspace;

import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WorkspaceManager implements WorkspaceService {
    private final List<String> workspaceFolders = new ArrayList<>();
    private final WorkspaceDocManager docManager = new WorkspaceDocManager();
    
    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        for (var event : params.getChanges()) {
            switch (event.getType()) {
                case Created, Changed -> docManager.updateFile(event.getUri());
                case Deleted -> docManager.deleteFile(event.getUri());
            }
        }
    }

    public WorkspaceDocManager getDocManager() {
        return docManager;
    }

    public void addWorkspaceFolders(List<WorkspaceFolder> folders) {
        folders.stream().map(WorkspaceFolder::getUri).forEach(workspaceFolders::add);
        docManager.resetAll(workspaceFolders);
    }

    public void removeWorkspaceFolders(List<WorkspaceFolder> folders) {
        folders.stream().map(WorkspaceFolder::getUri).forEach(workspaceFolders::remove);
        docManager.resetAll(workspaceFolders);
    }

    @Override
    public void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        addWorkspaceFolders(params.getEvent().getAdded());
        removeWorkspaceFolders(params.getEvent().getRemoved());
    }

    @Override
    public CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
        return WorkspaceService.super.symbol(params);
    }
}
