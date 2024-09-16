package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.generated;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;

import java.lang.invoke.MethodHandles;
import java.util.Map;

public interface GeneratedProgram {
    MethodHandles.Lookup PACKAGE_ACCESS = MethodHandles.lookup();
    
    Value run(Value.ObjectValue root, Map<String, Value> globals);
}
