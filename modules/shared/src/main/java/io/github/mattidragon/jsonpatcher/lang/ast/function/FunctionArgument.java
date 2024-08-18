package io.github.mattidragon.jsonpatcher.lang.ast.function;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import io.github.mattidragon.jsonpatcher.lang.ast.expression.Expression;

import java.util.Optional;

public record FunctionArgument(Target target, Optional<Expression> defaultValue) implements ProgramNode {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return defaultValue.stream().toList();
    }

    public sealed interface Target {
        record Variable(String name) implements Target {}
        
        enum Root implements Target {
            INSTANCE
        }
    }
}
