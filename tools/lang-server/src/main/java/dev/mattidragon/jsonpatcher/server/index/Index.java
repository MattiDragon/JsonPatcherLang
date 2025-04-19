package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;

import java.util.stream.Stream;

public interface Index {
    Stream<SourceSpan> find(IndexEntry entry);
    Stream<IndexEntry> lookupEntries(SourcePos pos);
}
