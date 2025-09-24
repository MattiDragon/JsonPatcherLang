package dev.mattidragon.jsonpatcher.cli.impl;

import picocli.CommandLine;

import java.io.IOException;

public class VersionProvider implements CommandLine.IVersionProvider {
    private static final String VERSION;

    static {
        try (var in = VersionProvider.class.getResourceAsStream("/version")) {
            if (in == null) {
                System.err.println("WARN: Version resource not found");
                VERSION = "unknown";
            } else {
                VERSION = new String(in.readAllBytes());
            }
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
