package dev.mattidragon.jsonpatcher.docs.parse;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import org.jspecify.annotations.Nullable;

public record DocParseDiagnostic(SourceSpan pos, String message, Type type) implements Diagnostic {
    @Override
    public @Nullable ProgramNode node() {
        return null;
    }

    @Override
    public String id() {
        return "DOC-" + type.ordinal();
    }

    @Override
    public Kind kind() {
        return type.kind;
    }

    public enum Type {
        EOL(Kind.ERROR),
        TYPE_PARSE(Kind.ERROR),
        DOC_PARSE(Kind.ERROR),
        CONDITION_PARSE_ERROR(Kind.ERROR),
        UNKNOWN_CONDITION(Kind.WARNING),
        INVALID_TAG(Kind.ERROR),
        UNKNOWN_TAG(Kind.WARNING);

        private final Kind kind;

        Type(Kind kind) {
            this.kind = kind;
        }
    }
}
