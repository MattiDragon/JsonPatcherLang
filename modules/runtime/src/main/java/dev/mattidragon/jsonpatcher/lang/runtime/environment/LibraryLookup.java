package dev.mattidragon.jsonpatcher.lang.runtime.environment;

import dev.mattidragon.jsonpatcher.lang.runtime_shared.Value;

public interface LibraryLookup {
    Value.ObjectValue findLibrary(String name);
}
