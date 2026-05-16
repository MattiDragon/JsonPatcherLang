package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record StringInterpolationExpression(List<String> parts, List<Expression> children) implements Expression {
    public StringInterpolationExpression {
        parts = List.copyOf(parts);
        children = List.copyOf(children);
        if (parts.size() != children.size() + 1) {
            throw new IllegalArgumentException("There must be exactly one more part than children");
        }
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return children;
    }
}
