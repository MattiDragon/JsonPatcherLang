package dev.mattidragon.jsonpatcher.lang.parse.metadata;

import java.util.Map;

public record MetadataObject(Map<String, MetadataElement> values) implements MetadataElement {
    public MetadataObject {
        values = Map.copyOf(values);
    }

    @Override
    public Iterable<? extends MetadataElement> getChildren() {
        return values.values();
    }
}
