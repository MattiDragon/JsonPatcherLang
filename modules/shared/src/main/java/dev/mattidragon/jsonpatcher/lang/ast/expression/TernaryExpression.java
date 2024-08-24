package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record TernaryExpression(Expression condition, Expression ifTrue, Expression ifFalse) implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(condition, ifTrue, ifFalse);
    }
}
