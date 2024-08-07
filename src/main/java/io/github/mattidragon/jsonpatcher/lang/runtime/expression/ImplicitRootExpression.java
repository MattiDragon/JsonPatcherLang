package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.List;

public record ImplicitRootExpression(String name, SourceSpan pos) implements Reference {
    @Override
    public Value get(EvaluationContext context) {
        return context.root().get(name, context.config(), pos);
    }

    @Override
    public void set(EvaluationContext context, Value value) {
        context.root().set(name, value, context.config(), pos);
    }

    @Override
    public void delete(EvaluationContext context) {
        context.root().remove(name, context.config(), pos);
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of();
    }
}
