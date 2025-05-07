package dev.mattidragon.jsonpatcher.docs.tree;

import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DocTreeObject {
    private @Nullable DocEntry entry;
    private final String name;
    private final Map<String, DocTreeProperty> properties = new HashMap<>();

    public DocTreeObject(String name) {
        this.name = name;
    }

    public void setEntry(DocEntry entry) {
        if (entry instanceof DocEntry.PropertyEntry) {
            throw new IllegalArgumentException("Object cannot have property entry");
        }
        if (entry instanceof DocEntry.NamespaceEntry) {
            throw new IllegalArgumentException("Object cannot have namespace entry");
        }
        if (!entry.name().equals(name)) {
            throw new IllegalArgumentException("Entry name does not match object name");
        }
        if (this.entry != null) {
            // We ignore duplicate entries
            return;
        }
        this.entry = entry;
    }

    public @Nullable DocEntry entry() {
        return entry;
    }

    public String name() {
        return name;
    }

    public void addProperty(DocTreeProperty property) {
        properties.putIfAbsent(property.entry().name(), property);
    }

    public Map<String, DocTreeProperty> properties() {
        return Collections.unmodifiableMap(properties);
    }
}
