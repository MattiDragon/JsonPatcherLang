package dev.mattidragon.jsonpatcher.server.index.symbol;

import dev.mattidragon.jsonpatcher.docs.data.NamespaceDescription;

public record DocEntrySymbol(NamespaceDescription namespace, String name) implements Symbol {
}
