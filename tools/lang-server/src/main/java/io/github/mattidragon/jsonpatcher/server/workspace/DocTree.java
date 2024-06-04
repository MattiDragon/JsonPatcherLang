package io.github.mattidragon.jsonpatcher.server.workspace;

import io.github.mattidragon.jsonpatcher.docs.data.DocEntry;
import org.jetbrains.annotations.Nullable;

import java.net.http.HttpRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocTree {
    private final Map<String, DocFile> files = new HashMap<>();
    private Map<String, ModuleDoc> moduleLookupByDocName = new HashMap<>();
    private Map<String, ModuleDoc> moduleLookup = new HashMap<>();
    private Map<String, TypeDoc> typeLookup = new HashMap<>();
    
    public synchronized void updateFile(String uri, List<DocEntry> entries) {
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
        
        files.put(uri, file);
        rebuildLookups();
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
