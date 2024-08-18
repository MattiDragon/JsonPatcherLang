package io.github.mattidragon.jsonpatcher.lang.ast.statement;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.Expression;

import java.util.List;

public record ForEachLoopStatement(Expression iterable, String variableName, Statement body
) implements Statement {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(iterable, body);
    }
}
