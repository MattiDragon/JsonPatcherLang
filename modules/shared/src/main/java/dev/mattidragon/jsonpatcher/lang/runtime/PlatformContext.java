package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.LangConfig;
import dev.mattidragon.jsonpatcher.lang.ast.function.PatchFunction;
import dev.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface PlatformContext {
    RuntimeException createException(String message);

    RuntimeException createException(String message, RuntimeException e);

    LangConfig config();

    Value execute(PatchFunction function, List<Value> args);

    void log(Value value);

    default @Nullable Value getLibraryProperty(Value value, String property) {
        return Libraries.getProperty(value, property);
    }
}
