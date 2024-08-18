package io.github.mattidragon.jsonpatcher.lang.ast.expression;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record VariableAccessExpression(String name) implements Reference {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}
