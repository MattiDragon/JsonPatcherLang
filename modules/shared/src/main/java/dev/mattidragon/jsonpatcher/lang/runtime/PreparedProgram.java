package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.LangConfig;

import java.util.function.Consumer;

public interface PreparedProgram {
    Value run(Consumer<RuntimeContextBuilder> contextBuilder, LangConfig config);
}
