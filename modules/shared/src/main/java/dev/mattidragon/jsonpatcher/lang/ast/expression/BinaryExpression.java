package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record BinaryExpression(Expression first, Expression second, Operator op) implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(first, second);
    }

    public enum Operator {
        PLUS,
        MINUS,
        MULTIPLY,
        DIVIDE,
        MODULO,
        EXPONENT,

        AND,
        OR,
        XOR,

        EQUALS,
        NOT_EQUALS,
        LESS_THAN,
        GREATER_THAN,
        LESS_THAN_EQUAL,
        GREATER_THAN_EQUAL,

        IN,
        ASSIGN
    }
}
