package dev.mattidragon.jsonpatcher.server.index.symbol;

public interface Symbol {
    /**
     * Returns true if all usages of a symbol are known to be within the file it's declared in.
     * This does not account for symbols that can be used across files, but aren't
     */
    boolean isLocal();
}
