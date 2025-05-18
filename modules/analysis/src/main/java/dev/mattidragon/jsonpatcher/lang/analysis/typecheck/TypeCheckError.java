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
        return Kind.ERROR;
    }

    public enum Code {
        UNEXPECTED_TYPE
    }
}
