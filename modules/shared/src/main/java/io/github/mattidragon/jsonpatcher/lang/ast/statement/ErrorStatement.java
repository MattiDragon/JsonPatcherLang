package io.github.mattidragon.jsonpatcher.lang.ast.statement;

import io.github.mattidragon.jsonpatcher.lang.PositionedException;
import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record ErrorStatement(PositionedException error) implements Statement {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}
