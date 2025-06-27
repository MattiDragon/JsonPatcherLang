package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.ValueType;

import java.util.List;

public record IsInstanceExpression(Expression input, ValueType type) implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(input);
    }
}
