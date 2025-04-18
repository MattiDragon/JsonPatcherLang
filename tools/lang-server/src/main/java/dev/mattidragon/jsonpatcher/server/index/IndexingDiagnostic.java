package dev.mattidragon.jsonpatcher.server.index;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import org.jspecify.annotations.Nullable;

public record IndexingDiagnostic(@Nullable ProgramNode node, @Nullable SourceSpan pos, String message, Code code) implements Diagnostic {
    @Override
    public String id() {
        return "INDEX-" + code.ordinal();
    }

    @Override
    public Kind kind() {
        return code.kind;
    }

    public enum Code {
        UNKNOWN_LIBRARY(Kind.WARNING),
        UNKNOWN_GLOBAL(Kind.WARNING);

        private final Kind kind;

        Code(Kind kind) {
            this.kind = kind;
        }
    }
}
