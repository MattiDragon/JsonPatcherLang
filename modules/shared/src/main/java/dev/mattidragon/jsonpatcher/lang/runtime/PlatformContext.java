package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;

import java.util.List;

public interface PlatformContext {
    RuntimeException createException(String message);

    RuntimeException createException(String message, RuntimeException e);

    LangConfig config();

    Value execute(PatchFunction function, List<Value> args);

    void log(Value value);
}
