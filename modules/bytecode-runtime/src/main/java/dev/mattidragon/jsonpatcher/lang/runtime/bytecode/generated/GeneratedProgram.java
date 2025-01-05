package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.generated;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;

import java.util.Map;

public interface GeneratedProgram {
    Value run(Value.ObjectValue root, Map<String, Value> globals);
}
