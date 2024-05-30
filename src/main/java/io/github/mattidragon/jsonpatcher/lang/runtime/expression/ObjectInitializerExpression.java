package io.github.mattidragon.jsonpatcher.lang.runtime.expression;

import io.github.mattidragon.jsonpatcher.lang.parse.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.runtime.EvaluationContext;
import io.github.mattidragon.jsonpatcher.lang.runtime.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.List;

public record ObjectInitializerExpression(List<Entry> contents, SourceSpan pos) implements Expression {
    public ObjectInitializerExpression {
        contents = List.copyOf(contents);
    }

    @Override
    public Value evaluate(EvaluationContext context) {
        var object = new Value.ObjectValue();
        contents.forEach((entry) -> object.value().put(entry.name, entry.value.evaluate(context)));
        return object;
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return contents.stream().map(Entry::value).toList();
    }
    
    public record Entry(String name, SourceSpan namePos, Expression value) {
    }
}
