package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.docs.newdocs.data.NewDocEntry;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.server.index.symbol.LibrarySymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DocsIndex {
    private final Map<IndexEntry, List<SourceSpan>> positions = new HashMap<>();

    public void index(List<NewDocEntry> docs, TreeMetadata metadata) {
        docs.forEach(entry -> indexEntry(entry, metadata));
    }

    private void indexEntry(NewDocEntry entry, TreeMetadata metadata) {
        switch (entry) {
            case NewDocEntry.GlobalEntry globalEntry -> {
            }
            case NewDocEntry.LibraryEntry libraryEntry -> {
                var indexEntry = new IndexEntry(new LibrarySymbol(libraryEntry.location().orElse(libraryEntry.name())), true);
                metadata.get(libraryEntry, MetadataKey.NAME_POS)
                        .ifPresent(buildPosConsumer(indexEntry));
            }
            case NewDocEntry.MetadataEntry metadataEntry -> {
            }
            case NewDocEntry.NamespaceEntry namespaceEntry -> {
            }
            case NewDocEntry.PropertyEntry propertyEntry -> {
            }
            case NewDocEntry.TypeAliasEntry typeAliasEntry -> {
            }
            case NewDocEntry.TypeDeclarationEntry typeDeclarationEntry -> {
            }
        }
    }

    private Consumer<SourceSpan> buildPosConsumer(IndexEntry entry) {
        return pos -> positions.computeIfAbsent(entry, e -> new ArrayList<>())
                .add(pos);
    }

    public void clear() {
        positions.clear();
    }

    public List<SourceSpan> getPositions(IndexEntry entry) {
        return positions.getOrDefault(entry, List.of());
    }
}
