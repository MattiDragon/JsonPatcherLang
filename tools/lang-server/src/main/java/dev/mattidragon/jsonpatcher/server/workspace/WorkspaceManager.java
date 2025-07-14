package dev.mattidragon.jsonpatcher.server.workspace;

import com.google.gson.JsonObject;
import dev.mattidragon.jsonpatcher.server.event.GlobalEventBus;
import dev.mattidragon.jsonpatcher.server.event.WorkspaceEventBus;
import dev.mattidragon.jsonpatcher.server.event.context.WorkspaceEventContext;
import dev.mattidragon.jsonpatcher.server.index.Index;
import dev.mattidragon.jsonpatcher.server.index.StaticCombinedIndex;
import dev.mattidragon.jsonpatcher.server.workspace.config.WorkspaceConfigManager;
import dev.mattidragon.jsonpatcher.server.workspace.settings.SettingsManager;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class WorkspaceManager implements WorkspaceService {
    private final List<String> workspaceFolders = new ArrayList<>();
    private final DocFileManager docFileManager;
    private final SettingsManager settingsManager;
    private final WorkspaceConfigManager workspaceConfigManager;
    private final BackgroundIndexManager backgroundIndexManager;
    private final WorkspaceEventBus eventBus;

    private final Index combinedIndex;

    public WorkspaceManager(GlobalEventBus globalEventBus) {
        this.eventBus = new WorkspaceEventBus(globalEventBus, new WorkspaceEventContext(this));
        docFileManager = new DocFileManager(eventBus);
        settingsManager = new SettingsManager(eventBus);
        backgroundIndexManager = new BackgroundIndexManager(eventBus);
        workspaceConfigManager = new WorkspaceConfigManager(eventBus);

        combinedIndex = new StaticCombinedIndex(docFileManager.getHolder().getIndex(), backgroundIndexManager.getIndex());
    }

    @Override
    public void didChangeConfiguration(DidChangeConfigurationParams params) {
        var settings = (JsonObject) params.getSettings();

        var jsonpatcherSettings = settings.getAsJsonObject("jsonpatcher");
        if (jsonpatcherSettings == null) jsonpatcherSettings = new JsonObject();

        var serverSettings = jsonpatcherSettings.getAsJsonObject("serverOptions");
        if (serverSettings == null) serverSettings = new JsonObject();

        settingsManager.update(serverSettings);
    }

    @Override
    public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        for (var event : params.getChanges()) {
            var uri = event.getUri();
            switch (event.getType()) {
                case Created, Changed -> {
                    docFileManager.updateFile(uri);
                    workspaceConfigManager.updateFile(uri);
                }
                case Deleted -> {
                    docFileManager.deleteFile(uri);
                    workspaceConfigManager.deleteFile(uri);
                }
            }
        }
    }

    public DocFileManager getDocFileManager() {
        return docFileManager;
    }

    public SettingsManager getSettingsManager() {
        return settingsManager;
    }

    public BackgroundIndexManager getBackgroundIndexManager() {
        return backgroundIndexManager;
    }

    public WorkspaceConfigManager getWorkspaceConfigManager() {
        return workspaceConfigManager;
    }

    public WorkspaceEventBus getEventBus() {
        return eventBus;
    }

    public void addWorkspaceFolders(List<WorkspaceFolder> folders) {
        folders.stream().map(WorkspaceFolder::getUri).forEach(workspaceFolders::add);
        docFileManager.resetAll(workspaceFolders);
        workspaceConfigManager.resetAll(workspaceFolders);
    }

    public void removeWorkspaceFolders(List<WorkspaceFolder> folders) {
        folders.stream().map(WorkspaceFolder::getUri).forEach(workspaceFolders::remove);
        docFileManager.resetAll(workspaceFolders);
        workspaceConfigManager.resetAll(workspaceFolders);
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

    public Index getWorkspaceIndex() {
        return combinedIndex;
    }
}
