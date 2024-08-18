package io.github.mattidragon.jsonpatcher.lang.ast.statement;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record BlockStatement(List<Statement> statements) implements Statement {
    public BlockStatement {
        statements = List.copyOf(statements);
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return statements;
    }
}
