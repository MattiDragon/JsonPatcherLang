package io.github.mattidragon.jsonpatcher.lang.runtime.statement;

import io.github.mattidragon.jsonpatcher.lang.parse.Parser;
import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;

import java.util.List;

public record ErrorStatement(Parser.ParseException error) implements Statement {
    @Override
    public void run(EvaluationContext context) {
        throw new IllegalStateException("Tried to execute error statement", error);
    }

    @Override
    public SourceSpan getPos() {
        return error.pos;
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}
