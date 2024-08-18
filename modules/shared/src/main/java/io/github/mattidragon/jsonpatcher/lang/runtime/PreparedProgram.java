package io.github.mattidragon.jsonpatcher.lang.runtime;

import io.github.mattidragon.jsonpatcher.lang.LangConfig;

import java.util.function.Consumer;

public interface PreparedProgram {
    void run(Consumer<ContextBuilder> contextBuilder, LangConfig config);
}
