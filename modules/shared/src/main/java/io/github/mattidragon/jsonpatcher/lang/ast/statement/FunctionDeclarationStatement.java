package io.github.mattidragon.jsonpatcher.lang.ast.statement;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.FunctionExpression;

import java.util.List;

public record FunctionDeclarationStatement(String name, FunctionExpression value) implements Statement {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(value);
    }
}
