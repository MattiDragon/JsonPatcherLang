package dev.mattidragon.jsonpatcher.lang.runtime_shared;

import org.jspecify.annotations.Nullable;

import java.util.List;

public interface PlatformContext {
    RuntimeException createException(String message);

    RuntimeException createException(String message, Exception cause);

    Value execute(PatchFunction function, List<Value> args);

    void log(Value value);

    @Nullable Value getLibraryProperty(Value value, String property);
}
