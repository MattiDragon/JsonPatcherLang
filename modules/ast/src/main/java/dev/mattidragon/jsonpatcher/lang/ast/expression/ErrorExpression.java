package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public record ErrorExpression(Diagnostic diagnostic, @Nullable Expression child) implements Reference {
    public ErrorExpression(Diagnostic diagnostic) {
        this(diagnostic, null);
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return child == null ? Collections.emptyList() : List.of(child);
    }
}
