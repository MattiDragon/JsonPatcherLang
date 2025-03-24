package dev.mattidragon.jsonpatcher.docs.newdocs.tree;

import dev.mattidragon.jsonpatcher.docs.newdocs.data.NamespaceDescription;
import dev.mattidragon.jsonpatcher.docs.newdocs.data.NewDocEntry;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DocTreeNamespace {
    private NewDocEntry.@Nullable NamespaceEntry entry;
    private final NamespaceDescription description;
    private final Map<String, DocTreeObject> objects = new HashMap<>();

    public DocTreeNamespace(NamespaceDescription description) {
        this.description = description;
    }

    public void setEntry(NewDocEntry.NamespaceEntry entry) {
        if (this.entry != null) {
            // We ignore duplicate entries
            return;
        }
        if (!entry.namespace().withLast(entry.name()).equals(description)) {
            throw new IllegalArgumentException("Entry does not match namespace description");
        }
        this.entry = entry;
    }

    public NewDocEntry.@Nullable NamespaceEntry entry() {
        return entry;
    }

    public NamespaceDescription description() {
        return description;
    }

    public DocTreeObject getOrCreateObject(String name) {
        return objects.computeIfAbsent(name, DocTreeObject::new);
    }

    public Map<String, DocTreeObject> objects() {
        return Collections.unmodifiableMap(objects);
    }
}
