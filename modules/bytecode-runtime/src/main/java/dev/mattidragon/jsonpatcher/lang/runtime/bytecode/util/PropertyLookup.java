package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import org.jspecify.annotations.Nullable;

public interface PropertyLookup {
    @Nullable Value getProperty(Value value, String name);
}
