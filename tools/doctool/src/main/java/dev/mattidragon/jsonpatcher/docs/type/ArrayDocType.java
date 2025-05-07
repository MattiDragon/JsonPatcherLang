package dev.mattidragon.jsonpatcher.docs.type;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

import java.util.List;

public record ArrayDocType(NewDocType elementType) implements NewDocType {
    @Override
    public Iterable<? extends MetadataHolder> getChildren() {
        return List.of(elementType);
    }
}
