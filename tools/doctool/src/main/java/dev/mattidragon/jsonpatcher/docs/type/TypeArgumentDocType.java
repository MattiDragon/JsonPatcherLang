package dev.mattidragon.jsonpatcher.docs.type;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

import java.util.List;

public record TypeArgumentDocType(FunctionDocType.TypeArgument owner) implements DocType {
    @Override
    public Iterable<? extends MetadataHolder> getChildren() {
        return List.of();
    }
}
