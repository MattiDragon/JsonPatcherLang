package io.github.mattidragon.jsonpatcher.lang.runtime.function;

import io.github.mattidragon.jsonpatcher.lang.runtime.expression.Expression;

import java.util.Optional;

public record FunctionArgument(Target target, Optional<Expression> defaultValue) {
    public sealed interface Target {
        record Variable(String name) implements Target {}
        
        enum Root implements Target {
            INSTANCE
        }
    }
}
