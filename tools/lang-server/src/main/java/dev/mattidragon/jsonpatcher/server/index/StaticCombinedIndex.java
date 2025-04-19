package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * A simple index that combines data from multiple sources.
 * This class is shallowly immutable, but the list and the indices within it may be mutated externally.
 */
public final class StaticCombinedIndex implements Index {
    private final List<Index> children;

    public StaticCombinedIndex(Index... children) {
        this(Arrays.asList(children));
    }

    public StaticCombinedIndex(List<Index> children) {
        this.children = children;
    }

    @Override
    public Stream<SourceSpan> find(IndexEntry entry) {
        return children.stream().flatMap(index -> index.find(entry));
    }

    @Override
    public Stream<IndexEntry> lookupEntries(SourcePos pos) {
        return children.stream().flatMap(index -> index.lookupEntries(pos));
    }
}
