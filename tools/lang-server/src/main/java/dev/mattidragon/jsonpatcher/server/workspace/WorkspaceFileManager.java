package dev.mattidragon.jsonpatcher.server.workspace;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class WorkspaceFileManager {
    protected static Optional<Path> getPath(String path) {
        try {
            var uri = new URI(path);
            return Optional.of(Path.of(uri));
        } catch (URISyntaxException e) {
            System.err.println("Failed to parse uri: " + e);
            return Optional.empty();
        } catch (FileSystemNotFoundException | IllegalArgumentException e) {
            // ignore, we'll just not use files from unknown uris
            return Optional.empty();
        }
    }

    protected abstract boolean isValidFile(Path path);

    protected abstract void createEntry(Path path);

    protected abstract boolean hasEntry(Path path);

    protected abstract void updateEntry(Path path);

    protected abstract void removeEntry(Path path);

    protected abstract void clearData();

    protected void afterChanges(List<Path> paths) {
    }

    public void resetAll(List<String> folders) {
        clearData();
        var changedPaths = new ArrayList<Path>();
        for (var folder : folders) {
            var path = getPath(folder);
            if (path.isEmpty()) continue;
            changedPaths.add(path.get());
            try (var stream = Files.walk(path.get())) {
                stream.forEach(file -> {
                    if (isValidFile(file)) {
                        createEntry(file);
                    }
                });
            } catch (IOException e) {
                System.err.println("Error while scanning files: " + e);
            }
        }
        afterChanges(changedPaths);
    }

    public void updateFile(String uri) {
        var path = getPath(uri).orElse(null);
        if (path == null) return;
        if (hasEntry(path)) {
            updateEntry(path);
        } else if (isValidFile(path)) {
            createEntry(path);
        }
        afterChanges(List.of(path));
    }

    public void deleteFile(String uri) {
        var path = getPath(uri);
        if (path.isEmpty()) return;
        removeEntry(path.get());
        afterChanges(List.of(path.get()));
    }
}
