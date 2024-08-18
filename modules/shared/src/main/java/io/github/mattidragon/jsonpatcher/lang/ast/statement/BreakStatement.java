package io.github.mattidragon.jsonpatcher.lang.ast.statement;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record BreakStatement() implements Statement {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }

    public static class BreakException extends RuntimeException {
        public BreakException() {
            super("Uncaught break statement");
        }
    }
}
