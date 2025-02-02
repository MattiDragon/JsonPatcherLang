package dev.mattidragon.jsonpatcher.server.workspace;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.parse.DocParser;
import dev.mattidragon.jsonpatcher.lang.error.DiagnosticsBuilder;
import dev.mattidragon.jsonpatcher.lang.parse.Lexer;
import dev.mattidragon.jsonpatcher.server.Util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Stores doc comments as linked to each other for all files in the workspace.
 * Many methods of this class are {@code synchronized} because it's possible for this class to be modified from multiple threads.
 * Only one instance of this class should exist and that instance should be managed by the {@link WorkspaceDocManager}.
 */
public class DocHolder {
    // TODO: Move stdlib out of compiler and reuse here (requires getting rid of interpreter)
    private static final List<String> STDLIB_FILES = List.of(
            "arrays.jsonpatch", "debug.jsonpatch", "functions.jsonpatch",
            "math.jsonpatch", "objects.jsonpatch", "strings.jsonpatch",
            "values.jsonpatch"
    );
    private final Map<String, FileData> files = new HashMap<>();
    private final Map<String, FileData> stdlibFiles = new HashMap<>();
    private final CompletableFuture<Void> stdlibFuture;
    private Map<String, ModuleData> moduleLookup = new HashMap<>();
    private Map<String, TypeData> typeLookup = new HashMap<>();
    private Map<String, OwnerData> ownerLookup = new HashMap<>();
    private Map<String, GlobalData> globalLookup = new HashMap<>();

    public DocHolder() {
        stdlibFuture = loadStdlib();
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

                futures = STDLIB_FILES.stream()
                        .map(fileName -> {
                            var cleanedName = fileName.substring(0, fileName.length() - ".jsonpatch".length());
                            return handleStdlibFile(fileName, tempDir).thenAccept(fileData -> {
                                synchronized (this) {
                                    stdlibFiles.put(cleanedName, fileData);
                                }
                            });
                        })
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
            try (var fileStream = DocHolder.class.getClassLoader().getResourceAsStream("stdlib_docs/" + fileName)) {
                if (fileStream == null) throw new FileNotFoundException("Couldn't find stdlib doc file '%s' in resources".formatted(fileName));

                var path = tempDirectory.resolve(fileName);
                Files.copy(fileStream, path, StandardCopyOption.REPLACE_EXISTING);

                // We ignore diagnostics here, but still need to collect them
                var diagnosticBuilder = new DiagnosticsBuilder();
                var docParser = new DocParser(diagnosticBuilder);
                Lexer.lex(Files.readString(path), path.toUri().toASCIIString(), diagnosticBuilder, docParser);
                
                return buildFile(path.toUri().toASCIIString(), docParser.getEntries());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to extract stdlib docs", e);
            }
        }, Util.EXECUTOR);
    }

    /**
     * Notifies the doc holder that a file has changed and its docs need to be reevaluated.
     * @param uri The uri of the changed file.
     * @param entries The new doc entries from the file.
     */
    public synchronized void updateFile(String uri, List<DocEntry> entries) {
        var file = buildFile(uri, entries);
        files.put(uri, file);
        rebuildLookups();
    }

    /**
     * Notifies the doc holder that a file should no longer be tracked,
     * usually due to it being deleted.
     * @param uri The uri of the file.
     */
    public synchronized void deleteFile(String uri) {
        files.remove(uri);
        rebuildLookups();
    }

    /**
     * Clears the doc holder of all docs except for stdlib docs.
     */
    public synchronized void clear() {
        files.clear();
        stdlibFiles.forEach((name, file) -> files.put("stdlib::" + name, file));
        rebuildLookups();
    }
    
    private static FileData buildFile(String uri, List<DocEntry> entries) {
        var file = new FileData(uri, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());

        for (var entry : entries) {
            switch (entry) {
                case DocEntry.Module module -> file.modules.put(module.name(), new ModuleData(file, module, new HashMap<>()));
                case DocEntry.Type type -> file.types.put(type.name(), new TypeData(file, type, new HashMap<>()));
                case DocEntry.Value value -> {}
                case DocEntry.GlobalModule module -> file.globalModules.put(module.name(), new GlobalModuleData(file, module, new HashMap<>()));
                case DocEntry.GlobalValue value -> file.globalValues.put(value.name(), new GlobalValueData(file, value));
            }
        }

        for (var entry : entries) {
            if (!(entry instanceof DocEntry.Value value)) continue;
            var module = file.modules.get(value.owner());
            if (module != null) {
                module.values.put(value.name(), value);
            }
            var type = file.modules.get(value.owner());
            if (type != null) {
                type.values.put(value.name(), value);
            }
            var globalModule = file.globalModules.get(value.owner());
            if (globalModule != null) {
                globalModule.values.put(value.name(), value);
            }
        }
        return file;
    }

    public synchronized Optional<DocHolder.GlobalData> getGlobal(String name) {
        return Optional.ofNullable(globalLookup.get(name));
    }

    public synchronized Map<String, DocHolder.GlobalData> getGlobals() {
        return Collections.unmodifiableMap(globalLookup);
    }

    /**
     * Gets the data of a doc entry able to own values (type or module).
     * Modules are looked up by their name, unlike in {@link #getModuleData}.
     * @param name The name of the type or module.
     * @return The data of the type or module, or {@link Optional#empty()} if not found.
     */
    public synchronized Optional<DocHolder.OwnerData> getOwnerData(String name) {
        return Optional.ofNullable(ownerLookup.get(name));
    }
    
    public synchronized Optional<DocHolder.ModuleData> getModuleData(String name) {
        return Optional.ofNullable(moduleLookup.get(name));
    }
    
    public synchronized Optional<DocHolder.TypeData> getTypeData(String name) {
        return Optional.ofNullable(typeLookup.get(name));
    }
    
    private synchronized void rebuildLookups() {
        moduleLookup = new HashMap<>();
        ownerLookup = new HashMap<>();
        globalLookup = new HashMap<>();
        typeLookup = new HashMap<>();
        for (var file : files.values()) {
            file.modules.values().forEach(module -> moduleLookup.put(module.entry.location(), module));
            ownerLookup.putAll(file.modules);
            ownerLookup.putAll(file.types);
            typeLookup.putAll(file.types);
            globalLookup.putAll(file.globalModules);
            globalLookup.putAll(file.globalValues);
        }
    }

    /**
     * Stores information about docs in a file.
     * @param uri The uri of the file. Used to tell the language client about which file to open.
     * @param modules A map of module names to module data.
     * @param types A map of type names to type data.
     */
    public record FileData(
            String uri,
            Map<String, ModuleData> modules,
            Map<String, GlobalModuleData> globalModules,
            Map<String, TypeData> types,
            Map<String, GlobalValueData> globalValues
    ) {
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
}
