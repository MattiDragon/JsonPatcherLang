package dev.mattidragon.jsonpatcher.lang.error;

@FunctionalInterface
public interface DiagnosticFilter {
    boolean shouldBlock(Diagnostic diagnostic);
}
