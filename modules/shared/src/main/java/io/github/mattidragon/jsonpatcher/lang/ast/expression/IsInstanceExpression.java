package io.github.mattidragon.jsonpatcher.lang.ast.expression;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record IsInstanceExpression(Expression input, Type type) implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(input);
    }

    public enum Type {
        NUMBER,
        STRING,
        BOOLEAN,
        ARRAY,
        OBJECT,
        NULL,
        FUNCTION
    }
}
