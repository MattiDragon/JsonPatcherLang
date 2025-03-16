package dev.mattidragon.jsonpatcher.docs.newdocs.tree;

import dev.mattidragon.jsonpatcher.docs.newdocs.data.NamespaceDescription;
import dev.mattidragon.jsonpatcher.docs.newdocs.data.NewDocEntry;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DocTree {
    private final Map<NamespaceDescription, DocTreeNamespace> namespaces = new HashMap<>();

    public DocTree(Collection<NewDocEntry> entries) {
        entries.forEach(this::addEntry);
    }

    public void addEntry(NewDocEntry entry) {
        switch (entry) {
            case NewDocEntry.NamespaceEntry namespaceEntry -> {
                var key = namespaceEntry.namespace().withLast(namespaceEntry.name());
                getOrCreateNamespace(key).setEntry(namespaceEntry);
            }
            case NewDocEntry.PropertyEntry propertyEntry ->
                    getOrCreateObject(propertyEntry.namespace(), propertyEntry.owner())
                            .addProperty(new DocTreeProperty(propertyEntry));
            default ->
                    getOrCreateObject(entry.namespace(), entry.name())
                            .setEntry(entry);
        }
    }

    private DocTreeObject getOrCreateObject(NamespaceDescription namespace, String owner) {
        return getOrCreateNamespace(namespace).getOrCreateObject(owner);
    }

    private DocTreeNamespace getOrCreateNamespace(NamespaceDescription namespace) {
        return namespaces.computeIfAbsent(namespace, DocTreeNamespace::new);
    }

    public Map<NamespaceDescription, DocTreeNamespace> namespaces() {
        return Collections.unmodifiableMap(namespaces);
    }
}
