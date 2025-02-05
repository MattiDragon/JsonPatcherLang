package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.environment;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;

public interface LibraryLookup {
    Value.ObjectValue findLibrary(String name);
}
