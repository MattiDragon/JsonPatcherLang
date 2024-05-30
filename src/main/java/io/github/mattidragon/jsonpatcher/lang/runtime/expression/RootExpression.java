package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.List;

public record RootExpression(SourceSpan pos) implements Expression {
    @Override
    public Value evaluate(EvaluationContext context) {
        return context.root();
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}
