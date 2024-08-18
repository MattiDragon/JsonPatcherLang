package io.github.mattidragon.jsonpatcher.lang.ast.expression;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.List;

public record ValueExpression(Value.Primitive value) implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}
