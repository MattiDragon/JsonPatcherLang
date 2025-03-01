package dev.mattidragon.jsonpatcher.docs.newdocs.parse;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import org.jspecify.annotations.Nullable;

public record DocParseError(SourceSpan pos, String message, Type type) implements Diagnostic {
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
        return Kind.ERROR;
    }

    public enum Type {
        EOL,
        TYPE_PARSE,
        DOC_PARSE
    }
}
