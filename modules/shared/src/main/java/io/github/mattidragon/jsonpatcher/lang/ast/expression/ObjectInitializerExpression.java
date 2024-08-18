package io.github.mattidragon.jsonpatcher.lang.ast.expression;

import io.github.mattidragon.jsonpatcher.lang.ast.SourceSpan;
import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;

public record ObjectInitializerExpression(List<Entry> contents) implements Expression {
    public ObjectInitializerExpression {
        contents = List.copyOf(contents);
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return contents.stream().toList();
    }

    public record Entry(String name, SourceSpan namePos, Expression value) implements ProgramNode {
        @Override
        public Iterable<? extends ProgramNode> getChildren() {
            return List.of(value);
        }
    }
}
