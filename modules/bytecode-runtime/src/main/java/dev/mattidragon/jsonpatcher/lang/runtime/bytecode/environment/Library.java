package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.environment;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class Library {
    private final LibraryGroup group;
    private final Supplier<Value.ObjectValue> contentSupplier;
    private final String name;
    private Value.@Nullable ObjectValue contents;

    public Library(LibraryGroup group, String name, Supplier<Value.ObjectValue> contents) {
        this.group = group;
        this.name = name;
        this.contentSupplier = contents;
    }

    public String name() {
        return name;
    }

    public LibraryGroup group() {
        return group;
    }

    public Value.ObjectValue contents() {
        if (contents == null) contents = new Value.ObjectValue(contentSupplier.get().value(), true);
        return contents;
    }

    @Override
    public String toString() {
        return "Library[" + name() + " group=" + group() + "]";
    }

}
