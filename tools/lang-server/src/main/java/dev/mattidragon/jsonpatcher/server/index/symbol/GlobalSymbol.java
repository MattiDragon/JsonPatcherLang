package dev.mattidragon.jsonpatcher.server.index.symbol;

public record GlobalSymbol(String name) implements Symbol {
    @Override
    public boolean isLocal() {
        return false;
    }
}
