package dev.mattidragon.jsonpatcher.lang.runtime;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public interface ContextBuilder {
    ContextBuilder root(Value.ObjectValue root);

    ContextBuilder variable(String name, String value);

    ContextBuilder variable(String name, boolean value);

    ContextBuilder variable(String name, Value value);

    ContextBuilder libraryLocator(LibraryLocator libraryLocator);

    ContextBuilder debugConsumer(Consumer<Value> debugConsumer);

    ContextBuilder stdlib(Map<String, Supplier<Value.ObjectValue>> stdlib);
}
