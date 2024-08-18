package io.github.mattidragon.jsonpatcher.lang.ast.expression;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record RootExpression() implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}
