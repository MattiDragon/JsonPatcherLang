package io.github.mattidragon.jsonpatcher.lang.ast.expression;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.ast.function.FunctionArguments;
import io.github.mattidragon.jsonpatcher.lang.ast.statement.Statement;

import java.util.List;

public record FunctionExpression(Statement body, FunctionArguments args) implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(args, body);
    }
}
