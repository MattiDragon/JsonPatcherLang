package dev.mattidragon.jsonpatcher.lang.runtime.util;

import dev.mattidragon.jsonpatcher.lang.runtime_shared.Value;
import dev.mattidragon.jsonpatcher.lang.runtime_shared.stdlib.Libraries;
import org.jspecify.annotations.Nullable;

public class DummyPropertyLookup implements PropertyLookup {
    public static final DummyPropertyLookup INSTANCE = new DummyPropertyLookup();

    private DummyPropertyLookup() {}

    @Override
    public @Nullable Value getProperty(Value value, String name) {
        return Libraries.getProperty(value, name);
    }
}
