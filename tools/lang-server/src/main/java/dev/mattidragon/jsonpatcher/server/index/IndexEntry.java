package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.server.index.symbol.Symbol;

public record IndexEntry(Symbol symbol, boolean isDeclaration) {
}
