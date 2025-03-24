package dev.mattidragon.jsonpatcher.server.index.symbol;

public record LibrarySymbol(String location) implements Symbol {
    @Override
    public boolean isLocal() {
        return false;
    }
}
