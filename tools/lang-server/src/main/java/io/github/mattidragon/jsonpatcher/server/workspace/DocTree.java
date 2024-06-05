package io.github.mattidragon.jsonpatcher.server.workspace;

import io.github.mattidragon.jsonpatcher.docs.data.DocEntry;
import io.github.mattidragon.jsonpatcher.docs.parse.DocParser;
import io.github.mattidragon.jsonpatcher.lang.parse.Lexer;
import io.github.mattidragon.jsonpatcher.server.Util;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class DocTree {
    private static final List<String> STDLIB_FILES = List.of(
            "arrays.jsonpatch", "debug.jsonpatch", "functions.jsonpatch",
            "math.jsonpatch", "objects.jsonpatch", "strings.jsonpatch"
    );
    private final Map<String, DocFile> files = new HashMap<>();
    private final Map<String, DocFile> stdlibFiles = new HashMap<>();
    private final CompletableFuture<Void> stdlibFuture;
    private Map<String, ModuleDoc> moduleLookupByDocName = new HashMap<>();
    private Map<String, ModuleDoc> moduleLookup = new HashMap<>();
    private Map<String, TypeDoc> typeLookup = new HashMap<>();

    public DocTree() {
        stdlibFuture = loadStdlib();
    }

    private CompletableFuture<Void> loadStdlib() {
        return CompletableFuture.runAsync(() -> {
            try {
                var tempDir = Path.of(System.getProperty("java.io.tmpdir")).resolve("jsonpatcher-temp-stdlib");
                Files.createDirectories(tempDir);

                List<CompletableFuture<Void>> futures;

                futures = STDLIB_FILES.stream()
                        .map(fileName -> {
                            var cleanedName = fileName.substring(0, fileName.length() - ".jsonpatch".length());
                            return handleStdlibFile(fileName, tempDir).thenAccept(docFile -> {
                                synchronized (this) {
                                    stdlibFiles.put(cleanedName, docFile);
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

    private static CompletableFuture<DocFile> handleStdlibFile(String fileName, Path tempDirectory) {
        return CompletableFuture.supplyAsync(() -> {
            try (var fileStream = DocTree.class.getClassLoader().getResourceAsStream("stdlib_docs/" + fileName)) {
                if (fileStream == null) throw new FileNotFoundException("Couldn't find stdlib doc file '%s' in resources".formatted(fileName));

                var path = tempDirectory.resolve(fileName);
                Files.copy(fileStream, path, StandardCopyOption.REPLACE_EXISTING);
                
                var docParser = new DocParser();
                Lexer.lex(Files.readString(path), fileName, docParser);
                
                return buildFile(path.toUri().toASCIIString(), docParser.getEntries());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to extract stdlib docs", e);
            }
        }, Util.EXECUTOR);
    }

    public synchronized void updateFile(String uri, List<DocEntry> entries) {
        var file = buildFile(uri, entries);

        files.put(uri, file);
        rebuildLookups();
    }

    private static DocFile buildFile(String uri, List<DocEntry> entries) {
        var file = new DocFile(uri, new HashMap<>(), new HashMap<>());

        for (var entry : entries) {
            switch (entry) {
                case DocEntry.Module module -> file.modules.put(module.name(), new ModuleDoc(file, module, new HashMap<>()));
                case DocEntry.Type type -> file.types.put(type.name(), new TypeDoc(file, type, new HashMap<>()));
                case DocEntry.Value value -> {}
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
        }
        return file;
    }

    public synchronized void deleteFile(String uri) {
        files.remove(uri);
        rebuildLookups();
    }
    
    public synchronized void clear() {
        files.clear();
        rebuildLookups();
    }
    
    @Nullable
    public ModuleDoc getStdlibModule(String name) {
        stdlibFuture.join();
        var file = stdlibFiles.get(name);
        if (file == null) return null;
        return file.modules().get(name);
    }
    
    @Nullable
    public synchronized OwnerDoc getOwner(String name) {
        var module = moduleLookupByDocName.get(name);
        if (module != null) return module;
        return typeLookup.get(name);
    }
    
    @Nullable
    public synchronized ModuleDoc getModuleData(String name) {
        return moduleLookup.get(name);
    }
    
    @Nullable
    public synchronized TypeDoc getTypeData(String name) {
        return typeLookup.get(name);
    }
    
    private synchronized void rebuildLookups() {
        moduleLookup = new HashMap<>();
        moduleLookupByDocName = new HashMap<>();
        typeLookup = new HashMap<>();
        for (DocFile file : files.values()) {
            file.modules.values().forEach(module -> moduleLookup.put(module.entry.location(), module));
            moduleLookupByDocName.putAll(file.modules);
            typeLookup.putAll(file.types);
        }
    }

    public record DocFile(String uri, Map<String, ModuleDoc> modules, Map<String, TypeDoc> types) {
    }
    
    public sealed interface OwnerDoc {
        DocEntry entry();
        Map<String, DocEntry.Value> values();
        DocFile owner();
    }
    
    public record ModuleDoc(DocFile owner, DocEntry.Module entry, Map<String, DocEntry.Value> values) implements OwnerDoc {
    }
    
    public record TypeDoc(DocFile owner, DocEntry.Type entry, Map<String, DocEntry.Value> values) implements OwnerDoc {
    }
}
