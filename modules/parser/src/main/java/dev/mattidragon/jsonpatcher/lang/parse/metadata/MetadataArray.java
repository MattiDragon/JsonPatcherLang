package dev.mattidragon.jsonpatcher.lang.parse.metadata;

import java.util.List;

public record MetadataArray(List<MetadataElement> values) implements MetadataElement {
    public MetadataArray {
        values = List.copyOf(values);
    }

    @Override
    public Iterable<? extends MetadataElement> getChildren() {
        return values;
    }
}
