package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public final class CombinedIndex implements Index {
    private final Set<Index> children = new HashSet<>();

    public void addChild(Index child) {
        children.add(child);
    }

    public void removeChild(Index child) {
        children.remove(child);
    }

    @Override
    public Stream<SourceSpan> find(IndexEntry entry) {
        return children.stream()
                .flatMap(index -> index.find(entry))
                .distinct();
    }

    @Override
    public Stream<IndexEntry> lookupEntries(SourcePos pos) {
        return children.stream()
                .flatMap(index -> index.lookupEntries(pos))
                .distinct();
    }
}
