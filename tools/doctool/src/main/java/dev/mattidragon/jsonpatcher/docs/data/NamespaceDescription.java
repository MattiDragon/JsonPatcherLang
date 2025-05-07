package dev.mattidragon.jsonpatcher.docs.data;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public record NamespaceDescription(List<String> parts) implements Comparable<NamespaceDescription> {
    public static final NamespaceDescription EMPTY = new NamespaceDescription(List.of());

    public NamespaceDescription {
        parts = List.copyOf(parts);
    }

    public NamespaceDescription withLast(String part) {
        var newParts = new ArrayList<>(parts);
        newParts.add(part);
        return new NamespaceDescription(newParts);
    }

    public NamespaceDescription withoutLast() {
        if (parts.isEmpty()) {
            return this;
        }
        var newParts = new ArrayList<>(parts);
        newParts.removeLast();
        return new NamespaceDescription(newParts);
    }

    @Override
    public int compareTo(@NotNull NamespaceDescription o) {
        for (int i = 0; i < Math.min(parts.size(), o.parts.size()); i++) {
            var part = parts.get(i);
            var otherPart = o.parts.get(i);
            var compare = part.compareTo(otherPart);
            if (compare != 0) {
                return compare;
            }
        }
        return Integer.compare(parts.size(), o.parts.size());
    }
}
