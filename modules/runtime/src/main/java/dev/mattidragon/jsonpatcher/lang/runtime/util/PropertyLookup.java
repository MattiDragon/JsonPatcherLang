package dev.mattidragon.jsonpatcher.lang.runtime.util;

import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;
import org.jspecify.annotations.Nullable;

public interface PropertyLookup {
    @Nullable Value getProperty(Value value, String name);
}
