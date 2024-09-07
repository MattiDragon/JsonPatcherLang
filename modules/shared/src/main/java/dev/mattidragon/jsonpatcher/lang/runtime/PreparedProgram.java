package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.LangConfig;

import java.util.function.Consumer;

public interface PreparedProgram {
    void run(Consumer<RuntimeContextBuilder> contextBuilder, LangConfig config);
}
