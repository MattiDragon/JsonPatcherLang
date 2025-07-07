package dev.mattidragon.jsonpatcher.docs.tree;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.docs.data.NamespaceDescription;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DocTree {
    private final Map<NamespaceDescription, DocTreeNamespace> namespaces = new HashMap<>();

    public DocTree() {
    }

    public DocTree(Collection<DocEntry> entries, @Nullable TreeMetadata metadata) {
        for (var entry : entries) {
            addEntry(entry, metadata);
        }
    }

    public void addEntry(DocEntry entry, @Nullable TreeMetadata metadata) {
        switch (entry) {
            case DocEntry.NamespaceEntry namespaceEntry -> {
                var key = namespaceEntry.namespace().withLast(namespaceEntry.name());
                getOrCreateNamespace(key).setEntry(namespaceEntry);
            }
            case DocEntry.PropertyEntry propertyEntry ->
                    getOrCreateObject(propertyEntry.namespace(), propertyEntry.owner())
                            .addProperty(new DocTreeProperty(propertyEntry, metadata));
            default ->
                    getOrCreateObject(entry.namespace(), entry.name())
                            .setEntry(entry);
        }
    }

    public void clear() {
        namespaces.clear();
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

    public void addAll(DocTree docTree) {
        docTree.namespaces.forEach((description, namespace) -> {
            var existingNamespace = getOrCreateNamespace(description);
            var oldEntry = namespace.entry();
            if (oldEntry != null) {
                existingNamespace.setEntry(oldEntry);
            }
            namespace.objects().forEach((name, object) -> {
                var existingObject = existingNamespace.getOrCreateObject(name);
                var oldObjectEntry = object.entry();
                if (oldObjectEntry != null) {
                    existingObject.setEntry(oldObjectEntry);
                }
                object.properties()
                        .values()
                        .forEach(existingObject::addProperty);
            });
        });
    }
}
