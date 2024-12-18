package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import dev.mattidragon.jsonpatcher.lang.runtime.stdlib.Libraries;
import org.jetbrains.annotations.Nullable;

public class DummyPropertyLookup implements PropertyLookup {
    public static final DummyPropertyLookup INSTANCE = new DummyPropertyLookup();

    private DummyPropertyLookup() {}

    @Override
    public @Nullable Value getProperty(Value value, String name) {
        return Libraries.getProperty(value, name);
    }
}
