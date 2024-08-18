package io.github.mattidragon.jsonpatcher.lang.ast.expression;

import io.github.mattidragon.jsonpatcher.lang.PositionedException;
import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record ErrorExpression(PositionedException error) implements Reference {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}
