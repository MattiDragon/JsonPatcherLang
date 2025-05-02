package dev.mattidragon.jsonpatcher.cli;

import picocli.CommandLine;

import java.io.IOException;

public class VersionProvider implements CommandLine.IVersionProvider {
    private static final String VERSION;

    static {
        try (var in = VersionProvider.class.getResourceAsStream("/version")) {
            if (in == null) {
                throw new IllegalStateException("Version resource not found");
            }
            VERSION = new String(in.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to get version", e);
        }
    }

    @Override
    public String[] getVersion() {
        return new String[]{
                "JsonPatcher CLI Tools v" + VERSION
        };
    }
}
