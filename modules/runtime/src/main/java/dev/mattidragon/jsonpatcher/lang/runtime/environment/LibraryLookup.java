package dev.mattidragon.jsonpatcher.lang.runtime.environment;

import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;

public interface LibraryLookup {
    Value.ObjectValue findLibrary(String name);
}
