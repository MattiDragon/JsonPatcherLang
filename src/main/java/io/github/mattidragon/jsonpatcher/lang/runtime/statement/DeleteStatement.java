package io.github.mattidragon.jsonpatcher.lang.runtime.statement;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.expression.Reference;

import java.util.List;

public record DeleteStatement(Reference target, SourceSpan pos) implements Statement {
    @Override
    public void run(EvaluationContext context) {
        target.delete(context);
    }

    @Override
    public SourceSpan getPos() {
        return pos;
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(target);
    }
}
