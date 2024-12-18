package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.util;

import dev.mattidragon.jsonpatcher.lang.ast.ValueType;
import dev.mattidragon.jsonpatcher.lang.runtime.Value;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public final class PropertyHolder implements PropertyLookup {
    private final Map<ValueType, Map<String, Value>> methods = new HashMap<>();

    @Override
    public @Nullable Value getProperty(Value value, String name) {
        if (value instanceof Value.ArrayValue array && name.equals("length")) {
            return new Value.NumberValue(array.value().size());
        }

        if (!methods.containsKey(value.type())) return null;
        var method = methods.get(value.type()).get(name);
        if (!(method instanceof Value.FunctionValue(var function))) return null;
        return new Value.FunctionValue(function.bind(value));
    }
    
    public void define(ValueType type, Map<String, Value> newMethods) {
        methods.computeIfAbsent(type, type1 -> new HashMap<>()).putAll(newMethods);
    }
}
