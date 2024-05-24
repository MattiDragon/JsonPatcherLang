package io.github.mattidragon.jsonpatcher.lang.runtime.function;

import java.util.List;
import java.util.Optional;

public record FunctionArguments(List<FunctionArgument> arguments, boolean varargs) {
    public FunctionArguments {
        arguments = List.copyOf(arguments);
    }
    
    public int requiredArguments() {
        return (int) arguments.stream().map(FunctionArgument::defaultValue).filter(Optional::isEmpty).count();
    }
}
