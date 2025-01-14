package dev.mattidragon.jsonpatcher.lang.analysis.poscheck;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;

public record MissingMetadataError(ProgramNode node, MetadataKey<SourceSpan> key) implements PosCheckError {
    @Override
    public String message() {
        return "Node %s is missing position metadata with key %s".formatted(node, key.name());
    }
}
