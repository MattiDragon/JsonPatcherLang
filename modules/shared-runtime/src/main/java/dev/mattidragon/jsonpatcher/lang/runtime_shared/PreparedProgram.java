package dev.mattidragon.jsonpatcher.lang.runtime_shared;

import dev.mattidragon.jsonpatcher.lang.error.LangConfig;

import java.util.function.Consumer;

public interface PreparedProgram {
    Value run(Consumer<RuntimeContextBuilder> contextBuilder, LangConfig config);
}
