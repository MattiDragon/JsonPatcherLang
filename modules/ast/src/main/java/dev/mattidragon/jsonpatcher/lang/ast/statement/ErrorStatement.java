package dev.mattidragon.jsonpatcher.lang.ast.statement;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.error.Diagnostic;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public record ErrorStatement(Diagnostic diagnostic, @Nullable ProgramNode child) implements Statement {
    public ErrorStatement(Diagnostic diagnostic) {
        this(diagnostic, null);
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return child == null ? Collections.emptyList() : List.of(child);
    }
}
