package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.lang.ast.SourcePos;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.server.document.PosLookup;

import java.util.stream.Stream;

/**
 * An {@link Index} implementation based on a {@link PosLookup}
 */
abstract class LookupIndex implements Index {
    private final String fileName;
    protected final PosLookup<IndexEntry> lookup = new PosLookup<>();

    LookupIndex(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public final Stream<SourceSpan> find(IndexEntry entry) {
        return lookup.getPositions(entry).stream();
    }

    @Override
    public final Stream<IndexEntry> lookupEntries(SourcePos pos) {
        if (!pos.file().name().equals(fileName)) {
            return Stream.empty();
        }
        return lookup.getAllAt(pos);
    }
}
