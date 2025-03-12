package dev.mattidragon.jsonpatcher.lang.runtime;

import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;

import java.util.Map;
import java.util.function.Supplier;

public class Libraries {
    /**
     * Libraries that can be imported by the user.
     */
    public static final Map<String, Supplier<Value.ObjectValue>> LOOKUP = Map.of();
}
