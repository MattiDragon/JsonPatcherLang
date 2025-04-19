package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;

import java.util.stream.Stream;

public final class EmptyIndex implements Index {
    @Override
    public Stream<SourceSpan> find(IndexEntry entry) {
        return Stream.empty();
    }

    @Override
    public Stream<IndexEntry> lookupEntries(SourcePos pos) {
        return Stream.empty();
    }
}
