package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record ShortedBinaryExpression(Expression first, Expression second, Operator op) implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(first, second);
    }

    public enum Operator {
        AND,
        OR
    }
}
