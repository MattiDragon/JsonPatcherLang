package io.github.mattidragon.jsonpatcher.lang.ast.function;

import io.github.mattidragon.jsonpatcher.lang.ast.ProgramNode;

import java.util.List;
import java.util.Optional;

public record FunctionArguments(List<FunctionArgument> arguments, boolean varargs) implements ProgramNode {
    public FunctionArguments {
        arguments = List.copyOf(arguments);
    }
    
    public int requiredArguments() {
        return (int) arguments.stream().map(FunctionArgument::defaultValue).filter(Optional::isEmpty).count();
    }

    @Override
    public Iterable<? extends ProgramNode> getChildren() {
        return arguments;
    }
}
