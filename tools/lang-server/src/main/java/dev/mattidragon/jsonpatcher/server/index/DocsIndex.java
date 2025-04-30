package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.docs.newdocs.DocMetadataKeys;
import dev.mattidragon.jsonpatcher.docs.newdocs.data.NewDocEntry;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.server.index.symbol.DocEntrySymbol;
import dev.mattidragon.jsonpatcher.server.index.symbol.GlobalSymbol;
import dev.mattidragon.jsonpatcher.server.index.symbol.LibrarySymbol;
import dev.mattidragon.jsonpatcher.server.index.symbol.PropertySymbol;

import java.util.List;

public class DocsIndex extends LookupIndex {
    public DocsIndex(String fileName) {
        super(fileName);
    }

    public void index(List<NewDocEntry> docs, TreeMetadata metadata) {
        docs.forEach(entry -> indexEntry(entry, metadata));
    }

    private void indexEntry(NewDocEntry entry, TreeMetadata metadata) {
        if (!(entry instanceof NewDocEntry.PropertyEntry)) {
            addSymbol(entry, new IndexEntry(new DocEntrySymbol(entry.namespace(), entry.name()), true), metadata);
        }

        switch (entry) {
            case NewDocEntry.GlobalLibraryEntry(var namespace, var name, var condition, var body) -> {
                var symbol = new GlobalSymbol(name);
                addSymbol(entry, new IndexEntry(symbol, true), metadata);
            }
            case NewDocEntry.GlobalValueEntry(var namespace, var name, var type, var condition, var body) -> {
                var symbol = new GlobalSymbol(name);
                addSymbol(entry, new IndexEntry(symbol, true), metadata);
            }
            case NewDocEntry.LibraryEntry(var namespace, var name, var location, var condition, var body) ->
                    addSymbol(entry, new IndexEntry(new LibrarySymbol(location.orElse(name)), true), metadata, MetadataKey.IMPORT_LOCATION_POS);
            case NewDocEntry.MetadataEntry metadataEntry -> {
            }
            case NewDocEntry.NamespaceEntry namespaceEntry -> {
            }
            case NewDocEntry.PropertyEntry(var namespace, var owner, var name, var type, var condition, var body) -> {
                addSymbol(entry, new IndexEntry(new DocEntrySymbol(namespace, owner), false), metadata, DocMetadataKeys.PROPERTY_OWNER_POS);
                addSymbol(entry, new IndexEntry(new PropertySymbol(namespace, owner, name), true), metadata);
            }
            case NewDocEntry.TypeAliasEntry typeAliasEntry -> {
            }
            case NewDocEntry.TypeDeclarationEntry typeDeclarationEntry -> {
            }
        }

        addNamespaceSymbols(entry, metadata);
    }

    private void addNamespaceSymbols(NewDocEntry entry, TreeMetadata metadata) {
        var partCount = entry.namespace().parts().size();
        metadata.get(entry, DocMetadataKeys.NAMESPACE_POSITIONS).ifPresent(namespacePositions -> {
            if (namespacePositions.size() != partCount) {
                throw new IllegalStateException("Namespace positions do not match namespace values");
            }

            var namespace = entry.namespace();
            for (var i = partCount - 1; i >= 0; i--) {
                var pos = namespacePositions.get(i);
                var name = namespace.parts().get(i);
                namespace = namespace.withoutLast();
                lookup.add(pos, new IndexEntry(new DocEntrySymbol(namespace, name), false));
            }
        });
    }

    private void addSymbol(NewDocEntry docEntry, IndexEntry entry, TreeMetadata metadata) {
        addSymbol(docEntry, entry, metadata, MetadataKey.NAME_POS);
    }

    private void addSymbol(NewDocEntry docEntry, IndexEntry entry, TreeMetadata metadata, MetadataKey<SourceSpan> metadataKey) {
        metadata.get(docEntry, metadataKey)
                .ifPresent(pos -> lookup.add(pos, entry));
    }
}
