package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record PropertyAccessExpression(Expression parent, String name) implements Reference {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(parent);
    }
}
