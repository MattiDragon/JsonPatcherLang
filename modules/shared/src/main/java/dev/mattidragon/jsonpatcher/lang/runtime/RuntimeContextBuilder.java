package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;

import java.util.function.Consumer;

public interface RuntimeContextBuilder {
    RuntimeContextBuilder root(Value.ObjectValue root);

    RuntimeContextBuilder libraryLocator(LibraryLocator libraryLocator);

    RuntimeContextBuilder debugConsumer(Consumer<Value> debugConsumer);
    
    RuntimeContextBuilder fillVariable(String name, Value value);
    
    default RuntimeContextBuilder addStdlib() {
        Libraries.BUILTIN.forEach((name, supplier) -> fillVariable(name, supplier.get()));
        return this;
    }
}
