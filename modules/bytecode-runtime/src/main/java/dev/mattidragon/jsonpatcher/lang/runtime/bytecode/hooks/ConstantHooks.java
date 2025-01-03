package dev.mattidragon.jsonpatcher.lang.runtime.bytecode.hooks;

import dev.mattidragon.jsonpatcher.lang.runtime.Value;

import java.lang.invoke.MethodHandles;

@SuppressWarnings("unused")
public class ConstantHooks {
    public static Value.NumberValue number(MethodHandles.Lookup caller, String name, Class<?> clazz, double value) {
        return new Value.NumberValue(value);
    }
    
    public static Value.StringValue string(MethodHandles.Lookup caller, String name, Class<?> clazz, String value) {
        return new Value.StringValue(value);
    }
}
