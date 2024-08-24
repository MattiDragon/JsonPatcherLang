package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record UnaryExpression(Expression input, Operator op) implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(input);
    }

    public enum Operator {
        NOT,
        MINUS,
        BITWISE_NOT,
        INCREMENT,
        DECREMENT
    }
}
