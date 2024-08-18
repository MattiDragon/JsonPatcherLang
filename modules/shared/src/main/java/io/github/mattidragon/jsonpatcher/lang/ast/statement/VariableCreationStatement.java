package io.github.mattidragon.jsonpatcher.lang.ast.statement;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.Expression;

import java.util.List;

public record VariableCreationStatement(String name, Expression initializer, boolean mutable) implements Statement {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(initializer);
    }
}
