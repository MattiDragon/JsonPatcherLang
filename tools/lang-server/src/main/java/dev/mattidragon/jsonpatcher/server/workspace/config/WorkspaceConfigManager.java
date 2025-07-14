package dev.mattidragon.jsonpatcher.server.workspace.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.mattidragon.jsonpatcher.server.event.WorkspaceEventBus;
import dev.mattidragon.jsonpatcher.server.event.workspace.WorkspaceConfigChangedEvent;
import dev.mattidragon.jsonpatcher.server.workspace.WorkspaceFileManager;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class WorkspaceConfigManager extends WorkspaceFileManager {
    private static final Gson GSON = new Gson();
    private final Map<Path, Entry> configs = new HashMap<>();

    private final WorkspaceEventBus eventBus;

    public WorkspaceConfigManager(WorkspaceEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    protected boolean isValidFile(Path path) {
        return path.getFileName().toString().equals("jsonpatcher-workspace.json");
    }

    @Override
    protected void createEntry(Path path) {
        try (var in = Files.newBufferedReader(path)) {
            var json = GSON.<@Nullable JsonObject>fromJson(in, JsonObject.class);
            var config = json == null ? null : WorkspaceConfig.tryParse(json);
            if (config == null) {
                System.err.println("Failed to parse workspace config file at " + path);
                return;
            }
            configs.put(path, new Entry(path.getParent(), config));
        } catch (IOException | RuntimeException e) {
            System.err.println("Failed to read workspace file " + path);
            e.printStackTrace(System.err);
        }
    }

    @Override
    protected boolean hasEntry(Path path) {
        return configs.containsKey(path);
    }

    @Override
    protected void updateEntry(Path path) {
        removeEntry(path);
        createEntry(path);
    }

    @Override
    protected void removeEntry(Path path) {
        configs.remove(path);
    }

    @Override
    protected void clearData() {
        configs.clear();
    }

    @Override
    protected void afterChanges(List<Path> changedPaths) {
        var filterPaths = changedPaths.stream().map(Path::getParent).toList();
        eventBus.fire(new WorkspaceConfigChangedEvent(
                this,
                uri -> getPath(uri)
                        .filter(path -> filterPaths.stream().anyMatch(path::startsWith))
                        .isPresent()
        ));
    }

    public LangVersion langVersion(String uri) {
        var active = getActiveEntries(uri);

        for (var entry : active) {
            var version = entry.config().version();
            if (version != null) {
                return version;
            }
        }
        return LangVersion.CURRENT;
    }

    public boolean externalStdlibNeeded(String uri) {
        var active = getActiveEntries(uri);

        for (var entry : active) {
            var externalStdlibNeeded = entry.config().externalStdlibNeeded();
            if (externalStdlibNeeded != null) {
                return externalStdlibNeeded;
            }
        }
        return true;
    }

    public List<String> allowedLibraryGroups(String uri) {
        var active = getActiveEntries(uri);

        for (var entry : active) {
            var allowedLibraryGroups = entry.config().allowedLibraryGroups();
            if (allowedLibraryGroups != null) {
                return allowedLibraryGroups;
            }
        }
        return List.of("default");
    }

    private List<Entry> getActiveEntries(String uri) {
        var path = getPath(uri);
        if (path.isEmpty()) return List.of();

        var matches = new ArrayList<Entry>();

        for (var entry : configs.values()) {
            if (path.get().startsWith(entry.path())) {
                matches.add(entry);
            }
        }
        matches.sort(Comparator.comparingInt(e -> e.path().getNameCount()));

        return matches;
    }

    private record Entry(Path path, WorkspaceConfig config) {
    }
}
