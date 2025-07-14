package dev.mattidragon.jsonpatcher.server.workspace;

import dev.mattidragon.jsonpatcher.docs.DocCommentHandler;
import dev.mattidragon.jsonpatcher.docs.tree.DocTree;
import dev.mattidragon.jsonpatcher.lang.ast.SourceFile;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.event.WorkspaceEventBus;
import dev.mattidragon.jsonpatcher.server.index.DocsIndex;
import dev.mattidragon.jsonpatcher.server.index.EmptyIndex;
import dev.mattidragon.jsonpatcher.server.index.Index;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class DocFileManager extends WorkspaceFileManager {
    private final Map<Path, Entry> entries = new HashMap<>();
    private final DocHolder docHolder;

    public DocFileManager(WorkspaceEventBus eventBus) {
        docHolder = new DocHolder(eventBus);
    }

    @Override
    protected boolean isValidFile(Path path) {
        return path.getFileName().toString().endsWith(".jsonpatch");
    }

    @Override
    protected void createEntry(Path path) {
        entries.put(path, new Entry(path.toUri().toASCIIString(), path));
    }

    @Override
    protected boolean hasEntry(Path path) {
        return entries.containsKey(path);
    }

    @Override
    protected void updateEntry(Path path) {
        entries.get(path).update();
    }

    @Override
    protected void removeEntry(Path path) {
        var removed = entries.remove(path);
        if (removed != null) {
            synchronized (removed) {
                removed.alive = false;
            }
        }
        docHolder.deleteFile(path.toUri().toASCIIString());
    }

    @Override
    protected void clearData() {
        docHolder.clear();
        entries.clear();
    }

    public DocHolder getHolder() {
        return docHolder;
    }

    public List<CompletableFuture<@Nullable SourceFile>> getSourceFiles() {
        return entries.values().stream()
                .map(entry -> entry.sourceFileFuture)
                .toList();
    }

    private class Entry {
        private final String uri;
        private final Path file;
        private volatile boolean alive = true;
        private volatile CompletableFuture<@Nullable SourceFile> sourceFileFuture = CompletableFuture.completedFuture(null);
        
        public Entry(String uri, Path file) {
            this.uri = uri;
            this.file = file;
            update();
        }
        
        private void update() {
            record DocsTuple(DocTree tree, Index index, TreeMetadata metadata) {}

            sourceFileFuture = CompletableFuture.<@Nullable SourceFile>supplyAsync(() -> {
                try {
                    var code = Files.readString(file);
                    return new SourceFile(uri, code);
                } catch (IOException e) {
                    return null;
                }
            }, Util.EXECUTOR);

            var docs = sourceFileFuture.thenApplyAsync(file -> {
                if (file == null || !alive) {
                    return new DocsTuple(new DocTree(), new EmptyIndex(), new TreeMetadata());
                }

                // We ignore diagnostics, but still have to collect them
                var diagnosticsBuilder = new DiagnosticsBuilder();
                var metadata = new TreeMetadata();
                var commentHandler = new DocCommentHandler(diagnosticsBuilder, metadata);
                Lexer.lex(file.code(), file.name(), diagnosticsBuilder, commentHandler);

                var index = new DocsIndex(uri);
                index.index(commentHandler.entries(), metadata);

                var tree = new DocTree(commentHandler.entries());
                return new DocsTuple(tree, index, metadata);
            }, Util.EXECUTOR);
            docs.thenAcceptAsync(tuple -> {
                if (!alive) return;
                synchronized (this) {
                    if (alive) {
                        docHolder.updateFile(uri, tuple.tree, tuple.metadata, tuple.index);
                    }
                }
            }, Util.EXECUTOR);
        }
    }
}
