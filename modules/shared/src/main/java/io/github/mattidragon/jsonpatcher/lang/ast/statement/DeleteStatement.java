package io.github.mattidragon.jsonpatcher.lang.ast.statement;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.Reference;

import java.util.List;

public record DeleteStatement(Reference target) implements Statement {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return List.of(target);
    }
}
