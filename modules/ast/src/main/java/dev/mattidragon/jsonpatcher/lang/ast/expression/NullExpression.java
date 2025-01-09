package dev.mattidragon.jsonpatcher.lang.ast.expression;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.Collections;

public record NullExpression() implements Expression {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return Collections.emptyList();
    }
}
