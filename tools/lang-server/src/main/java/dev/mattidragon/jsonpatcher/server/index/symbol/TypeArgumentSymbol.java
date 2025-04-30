package dev.mattidragon.jsonpatcher.server.index.symbol;

import dev.mattidragon.jsonpatcher.docs.newdocs.type.FunctionDocType;

public record TypeArgumentSymbol(FunctionDocType.TypeArgument typeArgument) implements Symbol {
}
