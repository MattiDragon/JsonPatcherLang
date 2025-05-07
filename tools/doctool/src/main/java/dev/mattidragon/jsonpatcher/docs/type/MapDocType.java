package dev.mattidragon.jsonpatcher.docs.type;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

import java.util.List;

public record MapDocType(DocType valueType) implements DocType {
    @Override
    public Iterable<? extends MetadataHolder> getChildren() {
        return List.of(valueType);
    }
}
