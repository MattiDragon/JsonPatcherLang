package dev.mattidragon.jsonpatcher.lang.runtime.reflection;

import dev.mattidragon.jsonpatcher.lang.runtime_shared.Value;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;

public class ReflectionInternalsLibrary {
    private final Map<Class<?>, JavaClassValue> classValueCache = new HashMap<>();

    public Value findClass(Value.StringValue value) {
        Class<?> clazz;
        try {
            clazz = Class.forName(value.value());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
        if (classValueCache.containsKey(clazz)) {
            return classValueCache.get(clazz);
        }

        var classValue = new JavaClassValue(clazz);
        classValueCache.put(clazz, classValue);
        return classValue;
    }

    public Value newArray(JavaClassValue clazz, Value.NumberValue size) {
        return new JavaObjectValue(Array.newInstance(clazz.clazz(), (int) size.value()));
    }
}
