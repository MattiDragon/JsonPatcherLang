package dev.mattidragon.jsonpatcher.lang.analysis.poscheck;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;

record IllegalPosNestingError(ProgramNode node, MetadataKey<SourceSpan> key, SourceSpan pos, SourceSpan parent) implements PosCheckError {
    @Override
    public String message() {
        return "Position %s on node %s (%s) is not within the parent pos (%s)".formatted(key.name(), node, pos.format(), parent.format());
    }

    @Override
    public String id() {
        return PosCheckDiagnostics.ILLEGAL_POS_NESTING;
    }
}
