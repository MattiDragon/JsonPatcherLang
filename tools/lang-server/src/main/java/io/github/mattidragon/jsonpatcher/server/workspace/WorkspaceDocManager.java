package io.github.mattidragon.jsonpatcher.server.workspace;

import io.github.mattidragon.jsonpatcher.docs.data.DocEntry;
import io.github.mattidragon.jsonpatcher.docs.parse.DocParser;
import io.github.mattidragon.jsonpatcher.lang.LangConfig;
import io.github.mattidragon.jsonpatcher.lang.parse.Lexer;
import io.github.mattidragon.jsonpatcher.server.Util;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class WorkspaceDocManager {
    private final LangConfig config;
    private final Map<Path, Entry> entries = new HashMap<>();
    private final DocHolder holder;

    public WorkspaceDocManager(LangConfig config) {
        this.config = config;
        holder = new DocHolder(config);
    }

    private static Optional<Path> getPath(String path) {
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
    
    private boolean isValidFile(Path path) {
        return path.getFileName().toString().endsWith(".jsonpatch");
    }
    
    public void resetAll(List<String> folders) {
        holder.clear();
        for (var folder : folders) {
            var path = getPath(folder);
            if (path.isEmpty()) continue;
            try (var stream = Files.walk(path.get())) {
                stream.forEach(file -> {
                    if (isValidFile(file)) {
                        entries.put(file, new Entry(file.toUri().toASCIIString(), file));
                    }
                });
            } catch (IOException e) {
                System.err.println("Error while scanning files: " + e);
            }
        }
    }
    
    public void updateFile(String uri) {
        var path = getPath(uri).orElse(null);
        if (path == null) return;
        if (entries.containsKey(path)) {
            entries.get(path).update();
        } else if (isValidFile(path)) {
            entries.put(path, new Entry(uri, path));
        }
    }
    
    public void deleteFile(String uri) {
        var path = getPath(uri);
        if (path.isEmpty()) return;
        var removed = entries.remove(path.get());
        if (removed != null) removed.alive = false;
        holder.deleteFile(uri);
    }

    public DocHolder getHolder() {
        return holder;
    }

    private class Entry {
        private final String uri;
        private final Path file;
        private volatile boolean alive = true;
        
        public Entry(String uri, Path file) {
            this.uri = uri;
            this.file = file;
            update();
        }
        
        private void update() {
            var docs = CompletableFuture.<List<DocEntry>>supplyAsync(() -> {
                try {
                    var code = Files.readString(file);
                    var docParser = new DocParser(config);
                    Lexer.lex(config, code, uri, docParser);
                    return docParser.getEntries();
                } catch (IOException e) {
                    return List.of();
                }
            }, Util.EXECUTOR);
            docs.thenAccept(entries -> {
                if (alive) {
                    holder.updateFile(uri, entries);
                }
            });
        }
    }
}
