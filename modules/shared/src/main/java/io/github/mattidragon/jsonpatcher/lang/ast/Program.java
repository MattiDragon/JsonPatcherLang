package io.github.mattidragon.jsonpatcher.lang.ast;

import io.github.mattidragon.jsonpatcher.lang.ast.statement.Statement;

import java.util.List;

public record Program(List<Statement> statements) implements ProgramNode {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return statements;
    }
}
