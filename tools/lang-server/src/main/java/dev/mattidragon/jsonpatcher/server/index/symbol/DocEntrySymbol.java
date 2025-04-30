package dev.mattidragon.jsonpatcher.server.index.symbol;

import dev.mattidragon.jsonpatcher.docs.newdocs.data.NamespaceDescription;

public record DocEntrySymbol(NamespaceDescription namespace, String name) implements Symbol {
}
