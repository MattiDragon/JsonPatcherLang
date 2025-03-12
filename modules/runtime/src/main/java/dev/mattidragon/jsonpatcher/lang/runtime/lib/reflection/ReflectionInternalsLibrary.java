package dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection;

import dev.mattidragon.jsonpatcher.lang.runtime.lib.reflection.remap.Remapper;
import dev.mattidragon.jsonpatcher.lang.runtime.value.Value;

import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unused")
public class ReflectionInternalsLibrary {
    private final Map<Class<?>, JavaClassValue> classValueCache = new HashMap<>();

    public Value findClass(Value.StringValue value) {
        Class<?> clazz;
        try {
            clazz = Class.forName(Remapper.COMBINED.remapClassToRuntime(value.value().replace('.', '/')).replace('/', '.'));
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

    /**
     * Cursed function which takes a jsonpatcher value and returns a {@link JavaObjectValue} of its internal representation.
     * This is needed for the string stdlib to call methods on the underlying java string.
     */
    public Value unwrapValue(Value input) {
        return switch (input) {
            case Value.ArrayValue(var value, var frozen) -> new JavaObjectValue(value);
            case Value.FunctionValue(var function) -> new JavaObjectValue(function);
            case Value.ObjectValue(var value, var frozen) -> new JavaObjectValue(value);
            case Value.NumberValue(var value) -> new JavaObjectValue(value);
            case Value.StringValue(var value) -> new JavaObjectValue(value);
            case Value.BooleanValue value -> new JavaObjectValue(value);
            case Value.NullValue.NULL -> throw new UnsupportedOperationException("Cannot unwrap null");
            case Value.SpecialValue specialValue -> throw new UnsupportedOperationException("Cannot unwrap special values");
        };
    }

    public Value forceWrap(Value input) {
        return new JavaObjectValue(input);
    }
}
