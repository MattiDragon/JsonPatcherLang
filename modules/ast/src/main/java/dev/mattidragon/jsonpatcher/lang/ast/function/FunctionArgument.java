package dev.mattidragon.jsonpatcher.lang.ast.function;

import dev.mattidragon.jsonpatcher.lang.ast.ProgramNode;
import dev.mattidragon.jsonpatcher.lang.ast.expression.Expression;

import java.util.Optional;

public record FunctionArgument(Target target, Optional<Expression> defaultValue) implements ProgramNode {
    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return defaultValue.stream().toList();
    }

    public sealed interface Target {
        record Variable(String name) implements Target {
            @Override
            public String toString() {
                return "Target.Variable[" + name + "]";
            }
        }
        
        enum Root implements Target {
            INSTANCE;

            @Override
            public String toString() {
                return "Target.Root";
            }
        }
    }
}
