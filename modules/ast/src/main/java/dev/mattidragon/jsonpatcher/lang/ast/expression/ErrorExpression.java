package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.error.PositionedException;
import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record ErrorExpression(PositionedException error) implements Reference {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}
