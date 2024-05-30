package io.github.mattidragon.jsonpatcher.lang.runtime.statement;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;

import java.util.List;

public record ContinueStatement(SourceSpan pos) implements Statement {
    @Override
    public void run(EvaluationContext context) {
        throw new ContinueException();
    }

    @Override
    public SourceSpan getPos() {
        return pos;
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }

    public static class ContinueException extends RuntimeException {
        public ContinueException() {
            super("Uncaught continue statement");
        }
    }
}
