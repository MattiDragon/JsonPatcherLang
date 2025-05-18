package dev.mattidragon.jsonpatcher.server.workspace;

import dev.mattidragon.jsonpatcher.docs.DocCommentHandler;
import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.data.NamespaceDescription;
import dev.mattidragon.jsonpatcher.docs.tree.DocTree;
import dev.mattidragon.jsonpatcher.docs.tree.DocTreeNamespace;
import dev.mattidragon.jsonpatcher.docs.tree.DocTreeObject;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.stdlib.Stdlib;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.index.DocsIndex;
import dev.mattidragon.jsonpatcher.server.index.DynamicCombinedIndex;
import dev.mattidragon.jsonpatcher.server.index.Index;
import dev.mattidragon.jsonpatcher.server.index.typing.DocTypeConverter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Stores doc comments as linked to each other for all files in the workspace.
 * Many methods of this class are {@code synchronized} because it's possible for this class to be modified from multiple threads.
 * Only one instance of this class should exist and that instance should be managed by the {@link WorkspaceDocManager}.
 */
public class DocHolder {
    private final Map<String, FileData> files = new HashMap<>();
    private final Map<String, FileData> stdlibFiles = new HashMap<>();
    private final DocTree completeTree = new DocTree(List.of());
    private final Map<String, ObjectData<DocEntry.GlobalEntry>> globals = new HashMap<>();
    private final Map<String, DocEntry.MetadataEntry> metadataTags = new HashMap<>();
    private final Map<String, DocEntry.LibraryEntry> libraries = new HashMap<>();
    private final DynamicCombinedIndex docIndex = new DynamicCombinedIndex();
    private DocTypeConverter typeConverter = new DocTypeConverter();

    private Runnable onRebuild = () -> {};

    public DocHolder() {
        loadStdlib().thenRun(this::rebuildLookups);
    }

    /**
     * Starts initialization of standard library docs. 
     * Initialization consists of creating a temporary directory and copying doc files from the jar there,
     * as well as loading their contents into {@link #stdlibFiles}.
     * @return A future which completes once stdlib docs are fully loaded and ready for use.
     */
    private CompletableFuture<Void> loadStdlib() {
        return CompletableFuture.runAsync(() -> {
            try {
                var tempDir = Path.of(System.getProperty("java.io.tmpdir")).resolve("jsonpatcher-temp-stdlib");
                Files.createDirectories(tempDir);

                List<CompletableFuture<Void>> futures;

                futures = Stdlib.LIBRARY_CONTENTS.keySet()
                        .stream()
                        .map(fileName -> handleStdlibFile(fileName, tempDir).thenAccept(fileData -> {
                            synchronized (this) {
                                stdlibFiles.put(fileName, fileData);
                            }
                        }))
                        .toList();
                
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            } catch (CompletionException | IOException e) {
                throw new IllegalStateException("Failed to prepare stdlib docs", e);
            }
        }, Util.EXECUTOR).exceptionally(e -> {
            System.err.println("Error while preparing stdlib docs:");
            e.printStackTrace(System.err);
            return null;
        });
    }

    private CompletableFuture<FileData> handleStdlibFile(String fileName, Path tempDirectory) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var path = tempDirectory.resolve(fileName + ".jsonpatch");
                Files.writeString(path, Stdlib.LIBRARY_CONTENTS.get(fileName));
                var uri = path.toUri().toASCIIString();

                // We ignore diagnostics here, but still need to collect them
                var diagnosticBuilder = new DiagnosticsBuilder();
                var metadata = new TreeMetadata();
                var commentHandler = new DocCommentHandler(diagnosticBuilder, metadata);
                Lexer.lex(Files.readString(path), uri, diagnosticBuilder, commentHandler);

                var index = new DocsIndex(uri);
                index.index(commentHandler.entries(), metadata);

                return new FileData(uri, new DocTree(commentHandler.entries()), index);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to extract stdlib docs", e);
            }
        }, Util.EXECUTOR);
    }

    /**
     * Notifies the doc holder that a file has changed and its docs need to be reevaluated.
     * @param uri The uri of the changed file.
     */
    public synchronized void updateFile(String uri, DocTree tree, Index index) {
        var file = new FileData(uri, tree, index);
        var old = files.put(uri, file);
        if (old != null) {
            docIndex.removeChild(old.index);
        }
        docIndex.addChild(index);
        rebuildLookups();
    }

    /**
     * Notifies the doc holder that a file should no longer be tracked,
     * usually due to it being deleted.
     * @param uri The uri of the file.
     */
    public synchronized void deleteFile(String uri) {
        var old = files.remove(uri);
        if (old != null) {
            docIndex.removeChild(old.index);
        }
        rebuildLookups();
    }

    /**
     * Clears the doc holder of all docs except for stdlib docs.
     */
    public synchronized void clear() {
        files.clear();
        docIndex.clear();
        stdlibFiles.forEach((name, file) -> files.put("stdlib::" + name, file));
        files.values().forEach(file -> docIndex.addChild(file.index));
        rebuildLookups();
    }

    public synchronized void onRebuild(Runnable callback) {
        var old = onRebuild;
        onRebuild = () -> {
            old.run();
            callback.run();
        };
    }

    public synchronized Optional<ObjectData<DocEntry.GlobalEntry>> getGlobal(String name) {
        return Optional.ofNullable(globals.get(name));
    }

    public synchronized Map<String, ObjectData<DocEntry.GlobalEntry>> getGlobals() {
        return Collections.unmodifiableMap(globals);
    }

    public Index getIndex() {
        return docIndex;
    }
    
    private synchronized void rebuildLookups() {
        completeTree.clear();
        for (var value : files.values()) {
            completeTree.addAll(value.newData());
        }

        globals.clear();
        libraries.clear();
        metadataTags.clear();
        for (var namespace : completeTree.namespaces().values()) {
            for (var object : namespace.objects().values()) {
                var entry = object.entry();
                if (entry == null) continue;

                switch (entry) {
                    case DocEntry.GlobalEntry globalEntry -> {
                        var data = new ObjectData<>(globalEntry, object);
                        globals.put(globalEntry.name(), data);
                    }
                    case DocEntry.LibraryEntry libraryEntry ->
                            libraries.put(libraryEntry.location().orElse(libraryEntry.name()), libraryEntry);
                    case DocEntry.MetadataEntry metadataEntry ->
                            metadataTags.put(metadataEntry.name(), metadataEntry);
                    default -> {}
                }
            }
        }
        typeConverter = new DocTypeConverter();
        typeConverter.loadTree(completeTree);

        onRebuild.run();
    }

    public Optional<DocEntry> getDocEntry(String fullName) {
        var parts = fullName.split("\\.");
        return getDocEntry(
                new NamespaceDescription(Arrays.asList(parts).subList(0, parts.length - 1)),
                parts[parts.length - 1]
        );
    }

    public Optional<DocEntry> getDocEntry(NamespaceDescription namespace, String name) {
        return Optional.ofNullable(completeTree.namespaces().get(namespace))
                .map(ns -> ns.objects().get(name))
                .map(DocTreeObject::entry)
                .or(() -> Optional.ofNullable(completeTree.namespaces().get(namespace.withLast(name)))
                        .map(DocTreeNamespace::entry));
    }

    public Optional<DocEntry> getLibrary(String location) {
            return Optional.ofNullable(libraries.get(location));
    }

    public Optional<DocTreeObject> getObject(NamespaceDescription namespace, String name) {
        return Optional.ofNullable(completeTree.namespaces().get(namespace))
                .map(ns -> ns.objects().get(name));
    }

    public DocTree getTree() {
        return completeTree;
    }

    public DocTypeConverter getTypeConverter() {
        return typeConverter;
    }

    public Map<String, DocEntry.MetadataEntry> getMetadataTags() {
        return metadataTags;
    }

    public record FileData(
            String uri,
            DocTree newData,
            Index index) {
        @Override
        public String toString() {
            return "FileData[%s]".formatted(uri);
        }
    }

    public record ObjectData<T extends DocEntry>(T entry, DocTreeObject treeObject) {
        public String name() {
            return entry.name();
        }
    }
}
