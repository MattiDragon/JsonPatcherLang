package dev.mattidragon.jsonpatcher.lang.analysis.typecheck.type;

import org.jspecify.annotations.Nullable;

public final class LazyType implements Type {
    private @Nullable Type value;

    public LazyType() {
    }

    public void set(Type value) {
        if (this.value != null) {
            throw new IllegalStateException("LazyType already set");
        }
        this.value = value;
    }

    public Type get() {
        if (value == null) {
            throw new IllegalStateException("LazyType not set");
        }
        return value;
    }
}
