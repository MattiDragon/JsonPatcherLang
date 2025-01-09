package dev.mattidragon.jsonpatcher.lang.ast.statement;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;

import java.util.Optional;

public record ReturnStatement(Optional<Expression> value) implements Statement {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return value.stream().toList();
    }
}
