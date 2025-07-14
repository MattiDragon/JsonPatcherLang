package dev.mattidragon.jsonpatcher.server.workspace.config;

public record LangVersion(int major, int minor, int patch) {
    public static final LangVersion CURRENT = new LangVersion(2, 0, 0);
}
