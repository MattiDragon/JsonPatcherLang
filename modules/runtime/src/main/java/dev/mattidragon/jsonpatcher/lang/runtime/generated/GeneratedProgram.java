package dev.mattidragon.jsonpatcher.lang.runtime.generated;

import dev.mattidragon.jsonpatcher.lang.runtime_shared.Value;

import java.util.Map;

public interface GeneratedProgram {
    Value run(Value.ObjectValue root, Map<String, Value> globals);
}
