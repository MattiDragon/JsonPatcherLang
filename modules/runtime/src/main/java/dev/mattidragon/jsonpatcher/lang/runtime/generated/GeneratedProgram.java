package dev.mattidragon.jsonpatcher.lang.runtime.generated;

import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;

import java.util.Map;

public interface GeneratedProgram {
    Value run(Value.ObjectValue root, Map<String, Value> globals);
}
