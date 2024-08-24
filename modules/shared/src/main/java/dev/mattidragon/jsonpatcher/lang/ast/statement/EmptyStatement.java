package dev.mattidragon.jsonpatcher.lang.ast.statement;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record EmptyStatement() implements Statement {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}
