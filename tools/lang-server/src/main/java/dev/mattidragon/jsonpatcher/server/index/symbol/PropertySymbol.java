package dev.mattidragon.jsonpatcher.server.index.symbol;

import dev.mattidragon.jsonpatcher.docs.newdocs.data.NamespaceDescription;

public record PropertySymbol(NamespaceDescription namespace, String owner, String name) implements Symbol {
    @Override
    public boolean isLocal() {
        return false;
    }
}
