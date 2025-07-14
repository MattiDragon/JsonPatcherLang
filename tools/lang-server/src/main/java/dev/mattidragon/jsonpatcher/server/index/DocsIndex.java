package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.docs.DocMetadataKeys;
import dev.mattidragon.jsonpatcher.docs.data.DocEntry;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.ast.meta.MetadataKey;
import dev.mattidragon.jsonpatcher.lang.ast.meta.TreeMetadata;
import dev.mattidragon.jsonpatcher.server.index.symbol.*;

import java.util.List;

public class DocsIndex extends LookupIndex {
    public DocsIndex(String fileName) {
        super(fileName);
    }

    public void index(List<DocEntry> docs, TreeMetadata metadata) {
        docs.forEach(entry -> indexEntry(entry, metadata));
    }

    private void indexEntry(DocEntry entry, TreeMetadata metadata) {
        if (!(entry instanceof DocEntry.PropertyEntry)) {
            addSymbol(entry, new IndexEntry(new DocEntrySymbol(entry.namespace(), entry.name()), true), metadata);
        }

        switch (entry) {
            case DocEntry.GlobalLibraryEntry(DocEntry.SharedData(var namespace, var name, var body, var tags, var metadata1)) -> {
                var symbol = new GlobalSymbol(name);
                addSymbol(entry, new IndexEntry(symbol, true), metadata);
            }
            case DocEntry.GlobalValueEntry(DocEntry.SharedData(var namespace, var name, var body, var tags, var metadata1), var type) -> {
                var symbol = new GlobalSymbol(name);
                addSymbol(entry, new IndexEntry(symbol, true), metadata);
            }
            case DocEntry.LibraryEntry(DocEntry.SharedData(var namespace, var name, var body, var tags, var metadata1), var location) -> {
                var symbol = new LibrarySymbol(location.orElse(name));
                addSymbol(entry, new IndexEntry(symbol, true), metadata, MetadataKey.IMPORT_LOCATION_POS);
            }
            case DocEntry.MetadataEntry metadataEntry -> {
                var symbol = new MetadataSymbol(entry.name());
                addSymbol(entry, new IndexEntry(symbol, true), metadata);
            }
            case DocEntry.NamespaceEntry namespaceEntry -> {
            }
            case DocEntry.PropertyEntry(DocEntry.SharedData(var namespace, var name, var body, var tags, var metadata1), var owner, var type) -> {
                addSymbol(entry, new IndexEntry(new DocEntrySymbol(namespace, owner), false), metadata, DocMetadataKeys.PROPERTY_OWNER_POS);
                addSymbol(entry, new IndexEntry(new PropertySymbol(namespace, owner, name), true), metadata);
            }
            case DocEntry.TypeAliasEntry typeAliasEntry -> {
            }
            case DocEntry.TypeDeclarationEntry typeDeclarationEntry -> {
            }
        }

        addNamespaceSymbols(entry, metadata);
    }

    private void addNamespaceSymbols(DocEntry entry, TreeMetadata metadata) {
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

    private void addSymbol(DocEntry docEntry, IndexEntry entry, TreeMetadata metadata) {
        addSymbol(docEntry, entry, metadata, MetadataKey.NAME_POS);
    }

    private void addSymbol(DocEntry docEntry, IndexEntry entry, TreeMetadata metadata, MetadataKey<SourceSpan> metadataKey) {
        metadata.get(docEntry, metadataKey)
                .ifPresent(pos -> lookup.add(pos, entry));
    }
}
