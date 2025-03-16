package dev.mattidragon.jsonpatcher.docs.newdocs.tree;

import dev.mattidragon.jsonpatcher.docs.newdocs.data.NewDocEntry;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DocTreeObject {
    private @Nullable NewDocEntry entry;
    private final String name;
    private final Map<String, DocTreeProperty> properties = new HashMap<>();

    public DocTreeObject(String name) {
        this.name = name;
    }

    public void setEntry(NewDocEntry entry) {
        if (entry instanceof NewDocEntry.PropertyEntry) {
            throw new IllegalArgumentException("Object cannot have property entry");
        }
        if (entry instanceof NewDocEntry.NamespaceEntry) {
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

    public @Nullable NewDocEntry entry() {
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
