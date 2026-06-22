package dev.mattidragon.jsonpatcher.lang.analysis.typecheck;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import org.jspecify.annotations.Nullable;

public record TypeCheckError(ProgramNode node, @Nullable SourceSpan pos, String message, Code code) implements Diagnostic {
    @Override
    public String id() {
        return "TYPE-" + code.ordinal();
    }

    @Override
    public Kind kind() {
        return code.kind;
    }

    public enum Code {
        UNEXPECTED_TYPE(Kind.ERROR),
        TYPE_WARNING(Kind.WARNING),
        UNEXPECTED_PROPERTY(Kind.ERROR),
        MISSING_PROPERTY(Kind.ERROR),
        ARGUMENT_COUNT_MISMATCH(Kind.ERROR);

        private final Kind kind;

        Code(Kind kind) {
            this.kind = kind;
        }
    }
}
