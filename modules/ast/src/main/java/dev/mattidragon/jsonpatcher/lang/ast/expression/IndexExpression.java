package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record IndexExpression(Expression parent, Expression index) implements Reference {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(parent, index);
    }
}
