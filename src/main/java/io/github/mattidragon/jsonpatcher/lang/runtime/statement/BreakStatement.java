package io.github.mattidragon.jsonpatcher.lang.runtime.statement;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;

import java.util.List;

public record BreakStatement(SourceSpan pos) implements Statement {
    @Override
    public void run(EvaluationContext context) {
        throw new BreakException();
    }

    @Override
    public SourceSpan getPos() {
        return pos;
    }

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
