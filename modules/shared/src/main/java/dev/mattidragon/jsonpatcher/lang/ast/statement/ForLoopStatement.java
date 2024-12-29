package dev.mattidragon.jsonpatcher.lang.ast.statement;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;

import java.util.List;

public record ForLoopStatement(Statement initializer, Expression condition, Statement incrementer, Statement body) implements Statement {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(initializer, condition, incrementer, body);
    }
}
