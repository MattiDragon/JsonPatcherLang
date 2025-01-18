package dev.mattidragon.jsonpatcher.lang.analysis.poscheck;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import org.jspecify.annotations.Nullable;

record MissingMetadataError(ProgramNode node, MetadataKey<SourceSpan> key) implements PosCheckError {
    @Override
    public @Nullable SourceSpan pos() {
        return null;
    }

    @Override
    public String id() {
        return PosCheckDiagnostics.MISSING_METADATA;
    }

    @Override
    public String message() {
        return "Node %s is missing position metadata with key %s".formatted(node, key.name());
    }
}
