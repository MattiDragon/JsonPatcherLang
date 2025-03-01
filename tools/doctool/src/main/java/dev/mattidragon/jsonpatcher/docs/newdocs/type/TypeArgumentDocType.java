package dev.mattidragon.jsonpatcher.docs.newdocs.type;

import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataHolder;

import java.util.List;

public record TypeArgumentDocType(FunctionDocType.TypeArgument owner) implements NewDocType {
    @Override
    public Iterable<? extends MetadataHolder> getChildren() {
        return List.of();
    }
}
