package dev.mattidragon.jsonpatcher.docs.newdocs.data;

import java.util.List;

public record NamespaceDescription(List<String> parts) {
    public static final NamespaceDescription EMPTY = new NamespaceDescription(List.of());

    public NamespaceDescription {
        parts = List.copyOf(parts);
    }
}
