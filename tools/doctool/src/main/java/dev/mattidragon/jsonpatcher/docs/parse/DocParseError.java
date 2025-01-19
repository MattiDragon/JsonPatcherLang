package dev.mattidragon.jsonpatcher.docs.parse;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import org.jspecify.annotations.Nullable;

public record DocParseError(SourceSpan pos, String message, Code code) implements Diagnostic {
    @Override
    public @Nullable ProgramNode node() {
        return null;
    }

    @Override
    public String id() {
        return "DOC-" + code.ordinal();
    }

    @Override
    public Kind kind() {
        return Kind.ERROR;
    }

    public enum Code {
        INVALID_HEADER,
        UNEXPECTED_CHARACTER,
        EOL,
        TRAILING_DATA,
        UNKNOWN_ENTRY_TYPE
    }
}
