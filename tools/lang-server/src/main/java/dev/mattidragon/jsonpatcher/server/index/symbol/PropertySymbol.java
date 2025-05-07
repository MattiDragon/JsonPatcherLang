package dev.mattidragon.jsonpatcher.server.index.symbol;

import dev.mattidragon.jsonpatcher.docs.data.NamespaceDescription;

public record PropertySymbol(NamespaceDescription namespace, String owner, String name) implements Symbol {
}
