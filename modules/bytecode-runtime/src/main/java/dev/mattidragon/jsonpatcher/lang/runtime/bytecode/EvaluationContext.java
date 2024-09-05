package dev.mattidragon.jsonpatcher.lang.runtime.bytecode;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime.PlatformContext;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.List;

public record EvaluationContext(LangConfig config) implements PlatformContext {
    // TODO: custom exception
    @Override
    public RuntimeException createException(String message) {
        return new RuntimeException(message);
    }

    @Override
    public RuntimeException createException(String message, RuntimeException e) {
        return new RuntimeException(message, e);
    }

    @Override
    public Value execute(PatchFunction function, List<Value> args) {
        // TODO: impl
        return null; 
    }

    @Override
    public void log(Value value) {
        // TODO: impl
    }
}
