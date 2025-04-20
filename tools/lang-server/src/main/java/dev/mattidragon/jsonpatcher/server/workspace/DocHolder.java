package dev.mattidragon.jsonpatcher.server.workspace;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.newdocs.DocCommentHandler;
import dev.mattidragon.jsonpatcher.docs.newdocs.data.NewDocEntry;
import dev.mattidragon.jsonpatcher.docs.newdocs.tree.DocTree;
import dev.mattidragon.jsonpatcher.docs.newdocs.tree.DocTreeObject;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.lang.stdlib.Stdlib;
import dev.mattidragon.jsonpatcher.server.Util;
import dev.mattidragon.jsonpatcher.server.index.DocsIndex;
import dev.mattidragon.jsonpatcher.server.index.DynamicCombinedIndex;
import dev.mattidragon.jsonpatcher.server.index.Index;

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
    private final Map<String, ObjectData<NewDocEntry.GlobalEntry>> globals = new HashMap<>();
    private final DynamicCombinedIndex docIndex = new DynamicCombinedIndex();

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

//    public synchronized Optional<DocHolder.GlobalData> getGlobal(String name) {
//        return Optional.ofNullable(globalLookup.get(name));
//    }
//
//    public synchronized Map<String, DocHolder.GlobalData> getGlobals() {
//        return Collections.unmodifiableMap(globalLookup);
//    }

    public synchronized Optional<ObjectData<NewDocEntry.GlobalEntry>> getGlobal(String name) {
        return Optional.ofNullable(globals.get(name));
    }

    public synchronized Map<String, ObjectData<NewDocEntry.GlobalEntry>> getGlobals() {
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

        for (var namespace : completeTree.namespaces().values()) {
            for (var object : namespace.objects().values()) {
                var entry = object.entry();
                if (entry == null) continue;

                switch (entry) {
                    case NewDocEntry.GlobalEntry globalEntry -> {
                        var data = new ObjectData<>(globalEntry, object);
                        globals.put(globalEntry.name(), data);
                    }
                    default -> {}
                }
            }
        }

        onRebuild.run();
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

    public sealed interface DocsData {
        /**
         * Returns the file defining this module or type
         */
        FileData file();

        /**
         * Returns the doc entry for this module or type
         */
        DocEntry entry();
    }

    /**
     * Superinterface for {@link ModuleData} and {@link TypeData} for cases where both are applicable.
     * Primarily used when dealing with value doc comments.
     */
    public sealed interface OwnerData extends DocsData {
        /**
         * Returns a map from value name to doc entry for values belonging to this module or type.
         */
        Map<String, DocEntry.Value> values();
    }

    public sealed interface GlobalData extends DocsData {
        /**
         * Returns the doc entry for this module or type
         */
        DocEntry.Global entry();
    }


    /**
     * Stores the docs of a single module and its values.
     * @param file The file defining the module
     * @param entry The doc entry for the module itself
     * @param values A map from value name to doc entry for said value
     */
    public record ModuleData(FileData file, DocEntry.Module entry, Map<String, DocEntry.Value> values) implements OwnerData {
    }

    /**
     * Stores the docs of a single global module and its values.
     * @param file The file defining the module
     * @param entry The doc entry for the module itself
     * @param values A map from value name to doc entry for said value
     */
    public record GlobalModuleData(FileData file, DocEntry.GlobalModule entry, Map<String, DocEntry.Value> values) implements OwnerData, GlobalData {
    }

    /**
     * Stores the docs of a single type and its values.
     * @param file The file defining the type
     * @param entry The doc entry for the type itself
     * @param values A map from value name to doc entry for said value
     */
    public record TypeData(FileData file, DocEntry.Type entry, Map<String, DocEntry.Value> values) implements OwnerData {
    }

    public record GlobalValueData(FileData file, DocEntry.GlobalValue entry) implements GlobalData {
    }

    public record ObjectData<T extends NewDocEntry>(T entry, DocTreeObject treeObject) {
        public String name() {
            return entry.name();
        }
    }
}
