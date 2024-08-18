package io.github.mattidragon.jsonpatcher.lang.ast.expression;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record ArrayInitializerExpression(List<Expression> contents) implements Expression {
    public ArrayInitializerExpression {
        contents = List.copyOf(contents);
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return contents;
    }
}
