package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record AssignmentExpression(Reference target, Expression value, BinaryExpression.Operator operator) implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(target, value);
    }
}
