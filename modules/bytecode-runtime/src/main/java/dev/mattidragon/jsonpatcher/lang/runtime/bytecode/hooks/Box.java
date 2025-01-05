package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

@SuppressWarnings("unused")
public final class Box {
    private @Nullable Value value;

    public Box() {
    }

    public Box(Value value) {
        this.value = value;
    }

    public Value getValue() {
        if (value == null) throw new IllegalStateException("Variable not assigned");
        return value;
    }

    public void setValue(Value value) {
        Objects.requireNonNull(value, "value may not be null");
        this.value = value;
    }
}
