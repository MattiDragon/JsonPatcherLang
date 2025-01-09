package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.List;

public record PrimitiveExpression(Value.Primitive value) implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}
