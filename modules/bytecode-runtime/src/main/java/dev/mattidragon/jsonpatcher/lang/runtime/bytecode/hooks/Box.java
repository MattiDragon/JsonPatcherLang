package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;

public final class Box {
    private Value value;

    public Value getValue() {
        if (value == null) throw new IllegalStateException("Variable not assigned");
        return value;
    }

    public void setValue(Value value) {
        if (value == null) throw new IllegalArgumentException("value may not be null");
        this.value = value;
    }
}
